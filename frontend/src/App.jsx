// ── import 문법 ────────────────────────────────────────────
// React 의 '훅(Hook)'을 가져온다. 훅은 함수 컴포넌트가 상태를 기억하거나
// 화면이 그려진 뒤 어떤 일을 하게 해주는 도구다.
//   useState  : 값을 기억하고, 값이 바뀌면 화면을 다시 그린다
//   useEffect : 화면이 그려진 뒤(또는 특정 값이 바뀔 때) 실행할 코드를 등록한다
import { useEffect, useState } from 'react'

import './index.css'

// ── 컴포넌트 밖에 상수를 둔 이유 ────────────────────────────
// 컴포넌트 안에 두면 화면이 다시 그려질 때마다 배열이 새로 만들어진다.
// 값은 같지만 '다른 객체'가 되어 불필요한 재계산이 생긴다.
// 바뀌지 않는 값은 밖에 두는 것이 관례다.
//
// 백엔드 Grade enum 과 순서·값을 맞췄다. 여기가 어긋나면
// 필터를 걸어도 아무것도 안 나오는 버그가 된다.
const GRADES = ['VIP', 'GOLD', 'GENERAL', 'UNKNOWN']

// 화면에 보여줄 한글 이름. 서버는 영문 enum 을 주고 화면에서만 번역한다.
// 서버가 한글을 주게 하면 API 가 한국어에 묶여서, 나중에 다국어나
// 다른 클라이언트가 붙을 때 곤란해진다.
const GRADE_LABELS = {
    VIP: 'VIP',
    GOLD: '골드',
    GENERAL: '일반',
    UNKNOWN: '미상',
}

const PAGE_SIZE = 10

/**
 * 앱 전체. 요구사항에 화면 이동이 없어서 라우터 없이 한 페이지에 섹션 2개를 둔다.
 *
 * ── 상태관리 라이브러리(Redux 등)를 안 쓴 이유 ──
 * 상태관리 도구는 "멀리 떨어진 여러 컴포넌트가 같은 데이터를 봐야 할 때" 쓴다.
 * 이 화면은 컴포넌트가 3개뿐이고, 공유해야 하는 것은
 * "업로드가 끝났으니 목록을 새로 불러와라"는 신호 하나다.
 * 그건 아래처럼 값을 내려주는 것으로 충분하다.
 * 도구를 넣으면 설정 파일과 개념(store, action, reducer)이 늘어나는데
 * 얻는 게 없다.
 */
export default function App() {
    // ── useState 문법 ──────────────────────────────────────
    // const [값, 값을바꾸는함수] = useState(초기값)
    // 대괄호는 '구조 분해 할당'이다. useState 가 배열 2개를 돌려주는데
    // 첫 번째를 reloadKey, 두 번째를 setReloadKey 라는 이름으로 받는다.
    //
    // 중요: reloadKey = 5 처럼 직접 대입하면 React 가 변화를 모른다.
    // 반드시 setReloadKey(...) 를 써야 화면이 다시 그려진다.
    //
    // ── reloadKey 라는 숫자를 쓰는 이유 ──
    // 업로드가 끝나면 목록을 다시 불러와야 한다.
    // 그런데 업로드 섹션과 목록 섹션은 서로를 모르는 별개 컴포넌트다.
    // 그래서 공통 부모(App)가 숫자를 하나 들고 있다가 업로드 성공 시 1을 올린다.
    // 목록 섹션은 그 숫자가 바뀌면 다시 조회한다.
    // 값 자체는 의미가 없고 "바뀌었다"는 사실만 신호로 쓴다.
    const [reloadKey, setReloadKey] = useState(0)

    return (
        <div>
            <h1>메시지 발송 관리</h1>

            {/*
              ── props 문법 ──
              onUploaded 는 부모가 자식에게 넘겨주는 값이다. 여기서는 함수를 넘긴다.
              자식은 언제 호출할지만 알고, 호출되면 무슨 일이 일어나는지는 부모가 정한다.

              k => k + 1 처럼 함수를 넘기는 이유:
              setReloadKey(reloadKey + 1) 로 쓰면 이 시점의 낡은 reloadKey 값을
              쓸 수 있다. 함수를 넘기면 React 가 항상 최신 값을 k 로 준다.
            */}
            <UploadSection onUploaded={() => setReloadKey((k) => k + 1)} />

            <RecipientSection reloadKey={reloadKey} />
        </div>
    )
}

/**
 * 요구사항 1: CSV 업로드 + 결과 표시
 */
function UploadSection({ onUploaded }) {
    const [file, setFile] = useState(null)       // 선택한 파일
    const [result, setResult] = useState(null)   // 서버가 준 업로드 결과
    const [error, setError] = useState(null)     // 실패 메시지
    const [loading, setLoading] = useState(false)

    // ── async / await 문법 ─────────────────────────────────
    // 서버 응답은 즉시 오지 않는다. await 는 "이 줄의 결과가 올 때까지 기다린 뒤
    // 다음 줄로 간다"는 뜻이고, 기다리는 동안 화면은 멈추지 않는다.
    // await 를 쓰려면 함수에 async 가 붙어 있어야 한다.
    async function handleUpload() {
        if (!file) {
            setError('CSV 파일을 먼저 선택하세요.')
            return
        }

        // 요청을 시작할 때 이전 결과와 에러를 지운다.
        // 안 지우면 두 번째 업로드가 실패했을 때 첫 번째 성공 결과가
        // 화면에 그대로 남아 있어서, 실패한 줄 모르고 넘어간다.
        setLoading(true)
        setError(null)
        setResult(null)

        try {
            // ── FormData ──
            // 파일은 JSON 으로 보낼 수 없다. JSON 은 글자만 담는 형식이고
            // 파일은 바이트 덩어리다.
            // FormData 를 쓰면 브라우저가 multipart/form-data 형식으로 만들어준다.
            //
            // 'file' 이라는 이름은 백엔드의 @RequestParam("file") 과 같아야 한다.
            // 다르면 스프링이 파일을 못 찾아 400 을 준다.
            const formData = new FormData()
            formData.append('file', file)

            // Content-Type 헤더를 직접 지정하지 않는다.
            // multipart 형식에는 각 부분을 구분하는 무작위 경계 문자열이 필요한데,
            // 브라우저가 그걸 만들어 헤더에 넣어준다. 직접 쓰면 그 값이 빠져서 깨진다.
            //
            // 주소가 '/api/...' 로 시작하는 상대 경로인 것에 주의.
            // http://localhost:8080 을 적지 않았다. vite.config.js 의 프록시가
            // 8080 으로 전달해주기 때문이고, 덕분에 배포 주소가 바뀌어도
            // 이 코드를 고칠 필요가 없다.
            const res = await fetch('/api/recipients/upload', {
                method: 'POST',
                body: formData,
            })

            // ── fetch 는 404, 500 에서도 예외를 던지지 않는다 ──
            // 네트워크가 끊긴 경우에만 던진다. 서버가 에러를 응답한 것은
            // "요청은 성공적으로 오갔다"고 보기 때문이다.
            // 그래서 res.ok(상태코드가 200~299인지)를 직접 확인해야 한다.
            // 이걸 빼먹으면 에러 응답을 정상 결과로 착각해 화면이 깨진다.
            if (!res.ok) {
                // 스프링의 기본 에러 응답에는 message 필드가 들어 있다.
                // JSON 이 아닐 수도 있으니 catch 로 감싸 실패를 흡수한다.
                const body = await res.json().catch(() => null)
                throw new Error(body?.message ?? `업로드 실패 (HTTP ${res.status})`)
            }

            setResult(await res.json())

            // 부모에게 알려서 목록이 다시 조회되게 한다.
            onUploaded()
        } catch (e) {
            setError(e.message)
        } finally {
            // finally 는 성공이든 실패든 반드시 실행된다.
            // 여기서 로딩을 끄지 않으면, 실패했을 때 버튼이 영원히
            // '업로드 중...' 상태로 잠긴다.
            setLoading(false)
        }
    }

    return (
        <section>
            <h2>1. 수신자 목록 업로드</h2>

            <input
                type="file"
                // 파일 선택창에서 CSV 를 먼저 보여준다. 강제는 아니다.
                accept=".csv,text/csv"
                // e 는 이벤트 객체. e.target 은 이 input 자체다.
                // files 는 목록이라 [0] 으로 첫 파일을 꺼낸다.
                // 선택을 취소하면 목록이 비므로 ?? null 로 방어한다.
                onChange={(e) => {
                    setFile(e.target.files[0] ?? null)
                    setError(null)
                }}
            />

            {/* disabled 로 중복 클릭을 막는다.
                안 막으면 조급한 사용자가 여러 번 눌러 같은 파일이 동시에 올라가고,
                두 요청이 서로를 '이미 등록된 번호'로 판정하는 혼란이 생긴다. */}
            <button onClick={handleUpload} disabled={loading} style={{ marginLeft: 8 }}>
                {loading ? '업로드 중...' : '업로드'}
            </button>

            {/* ── 조건부 렌더링 문법 ──
                {조건 && <JSX/>} 는 "조건이 참일 때만 이걸 그린다"는 뜻이다.
                error 가 null 이면 아무것도 안 그린다. if 문을 JSX 안에서
                쓸 수 없어서 이 방식을 쓴다. */}
            {error && <p className="error">{error}</p>}

            {result && <UploadResult result={result} />}
        </section>
    )
}

/**
 * 업로드 결과 표시.
 *
 * ── 컴포넌트를 따로 뺀 이유 ──
 * UploadSection 이 '요청 보내기'와 '결과 그리기' 둘 다 하면 길어진다.
 * 결과 표시는 상태가 필요 없고 받은 값을 그리기만 하므로 분리가 쉽다.
 */
function UploadResult({ result }) {
    // 걸러진 항목들을 한 배열로 묶는다. 아래에서 map 으로 한 번에 그리려는 것이다.
    // 이렇게 안 하면 똑같은 목록 그리기 코드를 4번 복사해야 한다.
    //
    // 네 갈래를 합치지 않고 따로 보여주는 이유:
    // 담당자가 해야 할 행동이 다르다.
    //   거절     -> 원본 파일을 고쳐 다시 올려야 한다
    //   파일내중복 -> 파일을 정리해야 한다
    //   이미등록   -> 아무것도 안 해도 된다 (정상)
    //   경고     -> 저장은 됐으니 확인만 하면 된다
    // "실패 8건"으로 합치면 정상 데이터를 다시 올리려고 헛수고를 한다.
    const groups = [
        { label: '거절 (저장 안 됨)', items: result.rejected },
        { label: '파일 내 중복 (앞선 행 유지)', items: result.duplicatedInFile },
        { label: '이미 등록된 번호 (건너뜀)', items: result.alreadyRegistered },
        { label: '경고 (저장됨)', items: result.warnings },
    ]

    return (
        <div>
            <div className="summary">
                <span>전체 {result.totalRows}행</span>
                <span>저장 {result.savedCount}건</span>
            </div>

            {/* filter 로 빈 목록은 아예 안 그린다.
                "거절 0건" 같은 항목이 4개 늘어서면 정작 문제가 눈에 안 들어온다. */}
            {groups
                .filter((g) => g.items.length > 0)
                .map((g) => (
                    // ── key 가 필요한 이유 ──
                    // React 는 목록이 바뀌었을 때 어느 항목이 그대로이고 어느 것이
                    // 새로 생겼는지 key 로 구분한다. 없으면 경고가 뜨고,
                    // 항목이 뒤섞일 때 엉뚱한 내용이 남아 있는 버그가 생긴다.
                    // 여기서는 label 이 서로 겹치지 않아 key 로 쓸 수 있다.
                    <div className="issue-group" key={g.label}>
                        {g.label} {g.items.length}건
                        <ul>
                            {g.items.map((issue) => (
                                // 행 번호는 한 목록 안에서 유일하므로 key 로 적합하다.
                                //
                                // 여기 표시되는 것이 행 번호와 사유뿐인 점에 주의.
                                // 이름과 번호는 서버 응답에 애초에 담기지 않는다.
                                // 업로드 결과도 화면이므로 마스킹 대상이기 때문이다.
                                <li key={issue.line}>
                                    {issue.line}행 — {issue.reason}
                                </li>
                            ))}
                        </ul>
                    </div>
                ))}
        </div>
    )
}

/**
 * 요구사항 2: 수신자 목록 조회 (등급 필터 + 페이징)
 * 요구사항 5: 마스킹된 번호 표시
 */
function RecipientSection({ reloadKey }) {
    const [grade, setGrade] = useState('')   // '' = 전체
    const [page, setPage] = useState(0)      // 서버와 같이 0부터 시작
    const [data, setData] = useState(null)
    const [error, setError] = useState(null)
    const [loading, setLoading] = useState(false)

    // ── useEffect 문법 ─────────────────────────────────────
    // useEffect(실행할함수, [감시할값들])
    // 화면이 처음 그려진 뒤 한 번 실행되고, 그 다음부터는 감시 목록의 값이
    // 바뀔 때마다 다시 실행된다.
    //
    // 여기서는 [grade, page, reloadKey] 를 감시한다.
    // 필터를 바꾸거나, 페이지를 넘기거나, 업로드가 끝나면 다시 조회한다.
    //
    // 감시 목록을 빼먹으면 매 렌더마다 실행되고, 그 안에서 상태를 바꾸므로
    // 또 렌더가 일어나 무한 반복에 빠진다. React 초보가 가장 흔히 겪는 사고다.
    useEffect(() => {
        // ── 이 플래그가 막는 문제 ──
        // 필터를 VIP -> GOLD 로 빠르게 바꾸면 요청이 두 번 나간다.
        // 응답이 반드시 보낸 순서대로 오지는 않아서, VIP 응답이 늦게 도착하면
        // 화면에는 GOLD 를 골라놨는데 VIP 목록이 표시된다.
        //
        // 아래 정리 함수가 이전 실행의 cancelled 를 true 로 만들어서,
        // 낡은 응답이 도착해도 화면에 반영하지 않게 한다.
        let cancelled = false

        async function load() {
            setLoading(true)
            setError(null)
            try {
                // URLSearchParams: 쿼리스트링을 안전하게 만들어준다.
                // 문자열을 직접 이어붙이면 특수문자 처리를 빠뜨리기 쉽다.
                const params = new URLSearchParams({ page, size: PAGE_SIZE })

                // 전체 조회일 때는 grade 파라미터를 아예 넣지 않는다.
                // 백엔드가 grade 없음(null)을 전체로 해석한다.
                if (grade) {
                    params.set('grade', grade)
                }

                const res = await fetch(`/api/recipients?${params}`)
                if (!res.ok) {
                    throw new Error(`목록 조회 실패 (HTTP ${res.status})`)
                }
                const json = await res.json()

                // 취소된 요청이면 결과를 버린다.
                if (!cancelled) setData(json)
            } catch (e) {
                if (!cancelled) setError(e.message)
            } finally {
                if (!cancelled) setLoading(false)
            }
        }

        load()

        // useEffect 가 돌려주는 함수는 '정리 함수'다.
        // 다음 실행 직전과 컴포넌트가 사라질 때 호출된다.
        return () => {
            cancelled = true
        }
    }, [grade, page, reloadKey])

    // 필터를 바꿀 때 페이지를 0으로 되돌린다.
    // 3페이지를 보다가 VIP 로 필터하면 VIP 는 3페이지가 없어서 빈 화면이 나온다.
    // 두 상태를 함께 바꾸므로 React 가 한 번만 다시 그린다.
    function handleGradeChange(e) {
        setGrade(e.target.value)
        setPage(0)
    }

    return (
        <section>
            <h2>2. 수신자 목록</h2>

            <label>
                등급 필터{' '}
                {/* select 의 value 를 상태와 묶는다(제어 컴포넌트).
                    이러면 화면에 보이는 선택값과 실제 상태가 항상 일치한다. */}
                <select value={grade} onChange={handleGradeChange}>
                    <option value="">전체</option>
                    {GRADES.map((g) => (
                        <option key={g} value={g}>
                            {GRADE_LABELS[g]}
                        </option>
                    ))}
                </select>
            </label>

            {error && <p className="error">{error}</p>}

            {/* 첫 로딩 때는 data 가 null 이라 아래 표를 그릴 수 없다.
                data?.content 처럼 물음표(옵셔널 체이닝)를 써도 되지만,
                조건을 나눠 쓰는 편이 읽기 쉽다. */}
            {loading && !data && <p className="muted">불러오는 중...</p>}

            {data && (
                <>
                    <table>
                        <thead>
                            <tr>
                                <th>이름</th>
                                <th>휴대폰번호</th>
                                <th>등급</th>
                                <th>등록일시</th>
                            </tr>
                        </thead>
                        <tbody>
                            {data.content.length === 0 && (
                                <tr>
                                    {/* colSpan: 셀 하나를 4칸 너비로 늘린다.
                                        안 쓰면 '없습니다'가 첫 칸에만 들어가 표가 어긋난다. */}
                                    <td colSpan={4} className="muted">
                                        조건에 맞는 수신자가 없습니다.
                                    </td>
                                </tr>
                            )}
                            {data.content.map((r) => (
                                // key 로 DB의 id 를 쓴다. 유일하고 안 바뀌는 값이라 가장 적합하다.
                                // 배열 순번(index)을 쓰면 정렬이나 필터로 순서가 바뀔 때
                                // 엉뚱한 행이 재활용되는 버그가 생긴다.
                                <tr key={r.id}>
                                    <td>{r.name}</td>
                                    {/* 서버가 이미 가려서 보낸 값을 그대로 표시한다.
                                        프론트에서 가리면 원본이 브라우저까지 내려온 것이므로,
                                        개발자도구 네트워크 탭에서 평문이 다 보인다.
                                        가리는 일은 반드시 서버에서 해야 한다. */}
                                    <td className="phone">{r.maskedPhone}</td>
                                    <td>{GRADE_LABELS[r.grade] ?? r.grade}</td>
                                    {/* toLocaleString: 브라우저 지역 설정에 맞춰 날짜를 보기 좋게 만든다.
                                        서버가 준 2026-08-30T00:19:32.747182 를 그대로 보여주면 읽기 어렵다. */}
                                    <td className="muted">
                                        {new Date(r.createdAt).toLocaleString('ko-KR')}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>

                    <div className="pager">
                        {/* page <= 0 이면 이전 버튼을 잠근다.
                            음수 페이지를 서버에 보내면 에러가 나므로 화면에서 막는다. */}
                        <button onClick={() => setPage((p) => p - 1)} disabled={page <= 0}>
                            이전
                        </button>

                        {/* 사람은 1부터 세므로 +1 해서 보여준다.
                            서버와 프론트 상태는 0부터인 채로 두고 표시만 바꾼다.
                            여기서 상태까지 1부터로 바꾸면 API 호출마다 -1 을 해야 하고
                            그걸 한 곳에서 빠뜨리는 순간 페이지가 밀린다. */}
                        <span>
                            {data.totalElements === 0 ? 0 : data.page + 1} / {data.totalPages} 페이지
                        </span>

                        {/* hasNext 는 서버가 계산해서 준 값이다.
                            프론트에서 page + 1 < totalPages 로 계산할 수도 있지만,
                            그 계산을 화면마다 다시 하다 보면 마지막 페이지에서 실수가 난다. */}
                        <button onClick={() => setPage((p) => p + 1)} disabled={!data.hasNext}>
                            다음
                        </button>

                        <span className="muted">총 {data.totalElements}건</span>

                        {/* 이미 데이터가 있는 상태에서 다시 조회 중일 때 표시.
                            표를 지우지 않고 옆에 작게 알리면 화면이 깜빡이지 않는다. */}
                        {loading && <span className="muted">갱신 중...</span>}
                    </div>
                </>
            )}
        </section>
    )
}