import { useState } from 'react'
import { runSetup, stopSetup } from '../api.js'

const STAGE_DESC = {
  1: 'HN API → 스토리 + 최상위 댓글 (수집 범위는 설정값)',
  2: 'article 행 존재 여부 조회',
  3: 'Moderation 검사 → 걸린 건만 LLM 마스킹',
  4: 'jsoup 요소 제거 → 길이 판정',
  5: '제목을 앵커로 본문만 추출',
  6: '구조화 출력 + 적합성 판정',
  7: '문단 단위 LLM 판단 · 하드 리밋 폴백',
  8: '요약 스토어 · 원문 청크 스토어',
}

const REASON_LABEL = {
  TOO_SHORT: 'JS 렌더링·조회 실패',
  PDF: 'PDF 바이너리',
  NO_BODY: '본문 없음',
  FETCH_FAILED: '조회 실패',
  NON_TECHNICAL: '비기술 주제',
  DUPLICATE: '중복 제출',
  UNSPECIFIED: '사유 미기재',
}

export default function SetupTab({ status }) {
  const [limit] = useState(100)
  const [error, setError] = useState(null)
  const running = status?.state === 'RUNNING'

  async function start() {
    setError(null)
    try {
      await runSetup(limit)
    } catch (e) {
      setError(e.message)
    }
  }

  if (!status) return <div className="page"><p className="sub">상태를 불러오는 중…</p></div>

  const { target, completed, skipped, inProgress, excluded, pending } = status
  const pct = (n) => (target > 0 ? (n / target) * 100 : 0)

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>데이터 셋업</h1>
          <p className="sub">Hacker News beststories 를 수집해 분석하고 검색 가능한 형태로 적재합니다</p>
        </div>
        <div style={{ display: 'flex', gap: 10 }}>
          {running && <button className="btn" onClick={stopSetup}>중단</button>}
          <button className={running ? 'btn btn-running' : 'btn btn-primary'}
                  onClick={start} disabled={running}>
            {running ? <><i className="badge-spin" /> 셋업 진행 중…</>
              : status.searchableStories > 0 || status.state !== 'IDLE' ? '다시 실행' : '셋업 시작'}
          </button>
        </div>
      </div>
      {error && <p className="sub" style={{ color: 'var(--excluded)' }}>{error}</p>}

      <div className="grid-kpi">
        <Kpi label="대상 스토리" value={target} foot="beststories 상위 100건" />
        <Kpi label="처리 완료" value={completed} foot="검색 대상으로 적재됨" color="var(--done)" />
        <Kpi label="건너뜀" value={skipped} foot="이미 처리된 스토리" color="var(--skipped)" />
        <Kpi label="처리 중" value={inProgress} foot={runningStageName(status) ?? '—'} color="var(--running)" />
        <Kpi label="제외" value={excluded} color="var(--excluded)"
             foot={`기계적 ${status.exclusions.mechanical.total} · 판정 ${status.exclusions.judged.total}`} />
      </div>

      <div className="card" style={{ marginBottom: 14 }}>
        <div className="card-body">
          <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
            <b>전체 진행률</b>
            <span className="num" style={{ color: 'var(--muted)', fontSize: 12 }}>
              {completed + skipped + inProgress + excluded} / {target} 처리됨
              {status.etaSeconds != null && ` · 예상 잔여 ${formatEta(status.etaSeconds)}`}
            </span>
          </div>
          {/* 완료·건너뜀·진행중·제외·대기 5색 세그먼트 */}
          <div className="progress">
            <i style={{ width: `${pct(completed)}%`, background: 'var(--done)' }} />
            <i style={{ width: `${pct(skipped)}%`, background: 'var(--skipped)' }} />
            <i style={{ width: `${pct(inProgress)}%`, background: 'var(--running)' }} />
            <i style={{ width: `${pct(excluded)}%`, background: 'var(--excluded)' }} />
            <i style={{ width: `${pct(pending)}%`, background: 'var(--pending)' }} />
          </div>
          <div className="legend">
            <span><i className="dot" style={{ background: 'var(--done)' }} />완료 <b className="num">{completed}</b></span>
            <span><i className="dot" style={{ background: 'var(--skipped)' }} />건너뜀 <b className="num">{skipped}</b></span>
            <span><i className="dot" style={{ background: 'var(--running)' }} />진행 중 <b className="num">{inProgress}</b></span>
            <span><i className="dot" style={{ background: 'var(--excluded)' }} />제외 <b className="num">{excluded}</b></span>
            <span><i className="dot" style={{ background: 'var(--pending)' }} />대기 <b className="num">{pending}</b></span>
          </div>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 520px', gap: 14, alignItems: 'start' }}>
        {/* 주 영역 — 이 화면의 목적은 "얼마나 남았나"가 아니라 "지금 무슨 일이 일어나고 있나"다 */}
        <section className="card">
          <div className="card-head">
            <h2>파이프라인 단계별 현황</h2>
            <span className="num" style={{ color: 'var(--muted)', fontSize: 12 }}>8단계</span>
          </div>
          <div className="card-body">
            {status.stages.map((s) => (
              <div className="stage" key={s.step}>
                <span className={`mark ${s.state.toLowerCase()}`}>
                  {s.state === 'DONE' ? '✓' : s.state === 'RUNNING' ? <i className="badge-spin" /> : '·'}
                </span>
                <div>
                  <div className="name">{s.step}. {s.name}</div>
                  <div className="desc">{STAGE_DESC[s.step]}</div>
                </div>
                <div className="right">
                  <span className="count" style={{ color: stageColor(s.state) }}>
                    {s.count.toLocaleString()} 건
                  </span>
                  <div className="bar">
                    <i style={{ width: `${s.progress * 100}%`, background: stageColor(s.state) }} />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>

        <div style={{ display: 'grid', gap: 14 }}>
          {/* "24건 제외" 만으로는 필터가 제대로 동작하는지 알 수 없다 */}
          <section className="card">
            <div className="card-head">
              <h2>제외 사유</h2>
              <span className="num" style={{ color: 'var(--excluded)', fontSize: 12 }}>{excluded}건</span>
            </div>
            <div className="card-body">
              <ReasonGroup title="기계적 정제 탈락" group={status.exclusions.mechanical} />
              <ReasonGroup title="LLM 판정 제외" group={status.exclusions.judged} />
              {status.exclusions.mechanical.total + status.exclusions.judged.total === 0
                && <p className="sub" style={{ margin: 0 }}>아직 제외된 스토리가 없습니다</p>}
            </div>
          </section>

          <section className="card">
            <div className="card-head">
              <h2>실시간 처리 로그</h2>
              {running && <span className="live"><i className="dot" style={{ background: 'var(--running)' }} />LIVE</span>}
            </div>
            <div className="card-body">
              {status.recentLogs.length === 0
                ? <p className="sub" style={{ margin: 0 }}>셋업을 시작하면 처리 내역이 흐릅니다</p>
                : status.recentLogs.map((l, i) => (
                  <div className="log" key={i}>
                    <time>{l.at}</time>
                    <span className={`tag ${l.kind}`}>{kindLabel(l.kind)}</span>
                    <div>
                      <div style={{ fontWeight: 600 }}>{l.title}</div>
                      <div className="meta">{l.meta}</div>
                    </div>
                  </div>
                ))}
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}

function Kpi({ label, value, foot, color }) {
  return (
    <div className="card kpi">
      <div className="label">{label}</div>
      <div className="value" style={{ color }}>{value.toLocaleString()}</div>
      <div className="foot">{foot}</div>
    </div>
  )
}

// 막대 분모는 그룹 자신의 합계다. 상단 KPI 의 제외 건수는 실행 중 카운터이고 사유별
// 건수는 DB 집계라 갱신 시점이 달라, 그 값을 분모로 쓰면 막대가 100% 를 넘는다.
function ReasonGroup({ title, group }) {
  const entries = Object.entries(group.reasons ?? {})
  if (entries.length === 0) return null
  const max = group.total || entries.reduce((sum, [, n]) => sum + n, 0)
  return (
    <>
      <div className="reason-group"><span>{title}</span><span className="num">{group.total}</span></div>
      {entries.map(([code, n]) => (
        <div className="reason" key={code}>
          <span>{REASON_LABEL[code] ?? code}</span>
          <span className="rbar"><i style={{ width: `${max ? Math.min(100, (n / max) * 100) : 0}%` }} /></span>
          <span className="rnum">{n}</span>
        </div>
      ))}
    </>
  )
}

const kindLabel = (k) => ({ ANALYZE: '분석', EXTRACT: '추출', EXCLUDE: '제외', CHUNK: '청킹' }[k] ?? k)
const stageColor = (s) => (s === 'DONE' ? 'var(--done)' : s === 'RUNNING' ? 'var(--running)' : 'var(--muted)')
const runningStageName = (s) => s.stages.find((x) => x.state === 'RUNNING')?.name
const formatEta = (sec) => (sec >= 60 ? `${Math.floor(sec / 60)}분 ${sec % 60}초` : `${sec}초`)
