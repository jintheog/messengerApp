package org.example.backend.recipient;

// JUnit 5: 자바 표준 테스트 도구. @Test 를 붙인 메서드를 찾아서 실행해준다.
import org.junit.jupiter.api.Test;

// 테스트 리포트에 표시될 '읽기 좋은 이름'을 따로 지정하는 어노테이션.
// 메서드 이름과 표시 이름을 분리하는 것이 이 어노테이션의 존재 이유다.
import org.junit.jupiter.api.DisplayName;

import java.io.InputStream;

// ── static import 문법 ────────────────────────────────────────
// 보통은 Assertions.assertThat(...) 처럼 클래스 이름을 붙여야 한다.
// import static 을 쓰면 클래스 이름을 생략하고 assertThat(...) 만 쓸 수 있다.
// 검증문이 수십 번 반복되므로 이렇게 하면 훨씬 읽기 쉽다.
// assertThat 은 AssertJ 라이브러리의 것이고, spring-boot-starter-test 에
// 이미 포함되어 있어서 build.gradle에 따로 추가할 게 없다.
//
// 주의: 이 라이브러리들은 build.gradle에서 testImplementation 으로 선언돼 있다.
// 즉 테스트 클래스패스에만 올라간다. 이 파일이 src/main/java 에 있으면
// @Test 와 assertThat 을 찾을 수 없다. 반드시 src/test/java 아래에 있어야 한다.
import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV 파서가 샘플 파일을 규칙대로 분류하는지 검증한다.
 *
 * ── 이 테스트에 @SpringBootTest 가 없는 것이 핵심이다 ──
 * @SpringBootTest 를 붙이면 스프링이 통째로 뜨고 MySQL 접속까지 시도한다.
 * 그러면 (1) 실행에 수십 초가 걸리고 (2) DB가 꺼져 있으면 테스트가 실패한다.
 * 검증하려는 것은 "문자열을 해석하는 규칙"뿐이므로 DB도 스프링도 필요 없다.
 *
 * RecipientCsvParser 를 DB를 모르는 순수 클래스로 분리해둔 이유가 바로 이것이다.
 * new RecipientCsvParser() 로 직접 만들어 쓸 수 있어서 1초 안에 끝난다.
 *
 * 클래스에 public 을 안 붙인 이유: JUnit 5는 패키지 접근 수준으로도 테스트를
 * 찾아 실행할 수 있다. 테스트는 외부에서 호출할 대상이 아니므로 관례상 생략한다.
 *
 * ── 메서드 이름은 영어, 표시 이름은 한국어로 분리한 이유 ──
 * 테스트 메서드 이름은 아무도 호출하지 않는다. 실패했을 때 리포트에 찍히는
 * '문서'가 유일한 용도이므로 한국어가 훨씬 빨리 읽힌다.
 * 그런데 메서드 이름 자체를 한글로 쓰면 컴파일러나 리포트 인코딩이 UTF-8이
 * 아닐 때 이름이 깨진다. 윈도우에서 특히 잘 터진다.
 * 그래서 식별자는 ASCII로 안전하게 두고, 사람이 읽을 문장은 @DisplayName 에
 * 맡겼다. 이러면 공백과 마침표도 자유롭게 쓸 수 있다.
 */
class RecipientCsvParserTest {

    /**
     * throws Exception: 아래 파일 읽기가 IOException 을 던질 수 있다.
     * 실제 코드라면 처리해야 하지만, 테스트에서는 예외가 나면 그냥
     * 실패로 처리되는 게 맞으므로 잡지 않고 밖으로 던진다.
     */
    @Test
    @DisplayName("샘플 파일 40행을 저장 32 / 파일내중복 2 / 거절 6 / 경고 1 로 분류한다")
    void classifiesSampleFileByRules() throws Exception {

        // ── getResourceAsStream 문법 ──────────────────────────
        // src/test/resources 폴더는 빌드하면 클래스패스 최상단에 복사된다.
        // 그래서 "/recipients_sample.csv" 처럼 슬래시로 시작하는 경로로 읽는다.
        //
        // C:\Users\... 같은 절대경로를 쓰지 않는 이유:
        // 그러면 내 컴퓨터에서만 돌아가는 테스트가 된다. 평가자가 받아서 실행하면
        // 경로가 없어서 실패한다. 클래스패스로 읽으면 프로젝트만 있으면 돌아간다.
        //
        // try-with-resources: 괄호 안에서 만든 스트림을 블록이 끝나면 자동으로 닫는다.
        try (InputStream in = getClass().getResourceAsStream("/recipients_sample.csv")) {

            CsvParseResult result = new RecipientCsvParser().parse(in);

            // ── 왜 이 5개 숫자인가 ────────────────────────────
            // 요구사항이 "이 샘플 파일을 업로드했을 때 동작해야 한다"고 명시했으므로,
            // 내가 세운 정책이 실제로 그 파일에 어떻게 적용되는지가 곧 정답이다.
            // 샘플 40행을 손으로 대조해 나온 값이다.

            // 헤더 1줄과 맨 끝 빈 줄을 뺀 데이터 행 수
            assertThat(result.totalRows()).isEqualTo(40);

            // 저장 후보 32건
            assertThat(result.recipients()).hasSize(32);

            // 파일 안에서 앞선 행과 번호가 겹친 2건 (28행, 38행)
            assertThat(result.duplicatedInFile()).hasSize(2);

            // 규칙 위반으로 저장하지 않은 6건
            assertThat(result.rejected()).hasSize(6);

            // 저장은 했지만 알려야 하는 1건 (등급 빈 값 -> UNKNOWN)
            assertThat(result.warnings()).hasSize(1);
        }
    }

    /**
     * 위 테스트는 "6건 거절"만 확인한다. 그런데 우연히 다른 행 6개가
     * 거절돼도 통과해버린다. 개수만 맞으면 통과하는 테스트는 안전망이 약하다.
     *
     * 그래서 "몇 번째 행이 왜 거절됐는지"까지 고정한다.
     * 이렇게 해두면 나중에 정규화 규칙을 건드렸을 때 어느 판정이 깨졌는지
     * 바로 드러난다.
     */
    @Test
    @DisplayName("거절된 행의 위치와 사유가 정확하다")
    void reportsExactLineNumbersAndReasons() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/recipients_sample.csv")) {
            CsvParseResult result = new RecipientCsvParser().parse(in);

            // ── extracting 문법 ──────────────────────────────
            // result.rejected() 는 List<RowIssue> 다.
            // extracting(RowIssue::line) 은 그 목록에서 line 값만 뽑아
            // [29, 32, 33, 34, 39, 40] 같은 목록으로 바꿔준다.
            //
            // RowIssue::line 은 '메서드 참조'다. RowIssue 객체를 받아서
            // .line() 을 호출하는 함수를 짧게 쓴 것이고,
            // issue -> issue.line() 과 같은 뜻이다.
            //
            // containsExactly: 값과 '순서'까지 정확히 같아야 통과한다.
            // 순서까지 검증하는 이유 - 파서가 파일을 위에서 아래로 훑으므로
            // 결과도 행 번호 순이어야 정상이다. 순서가 뒤섞이면
            // 화면에 사유가 뒤죽박죽 뜨는 문제가 되므로 함께 고정한다.
            //
            // 각 행이 걸린 이유:
            //   29행 1012345678      -> 앞자리 0 누락
            //   32행 02-555-1234     -> 9자리
            //   33행 (번호 없음)      -> 필수값 누락
            //   34행 (이름 없음)      -> 필수값 누락
            //   39행 012-88112-2233  -> 12자리
            //   40행 012-7070        -> 7자리
            assertThat(result.rejected())
                    .extracting(RowIssue::line)
                    .containsExactly(29, 32, 33, 34, 39, 40);

            // 행 번호가 파일에서 눈에 보이는 줄 번호와 맞는지 확인한다.
            // 파서에서 getRecordNumber() + 1 을 한 이유가 이것이다.
            // 담당자가 엑셀로 파일을 열면 헤더가 1행이므로, 이 번호를 그대로
            // 찾아 들어갈 수 있어야 한다.
            //
            // 28행은 2행과, 38행은 3행과 번호가 겹친다. 먼저 온 행이 살아남는다.
            assertThat(result.duplicatedInFile())
                    .extracting(RowIssue::line)
                    .containsExactly(28, 38);

            // 35행: 표승현,012-5959-6060,  <- 등급이 빈 값
            assertThat(result.warnings())
                    .extracting(RowIssue::line)
                    .containsExactly(35);

            // ── 사유 문장까지 검증하는 이유 ────────────────────
            // 29행 "1012345678" 은 10자리다. 그냥 자릿수 검사에 맡기면
            // "자릿수가 맞지 않습니다"라는, 틀리지는 않았지만 쓸모없는 사유가 나온다.
            // 실제 원인은 엑셀이 앞자리 0을 날린 것이므로 사유를 따로 만들었고,
            // 그 판정이 살아 있는지를 여기서 지킨다.
            //
            // get(0) - rejected 목록의 첫 번째 항목. 위에서 순서를 고정했으므로
            // 이게 29행이라는 것이 보장된다.
            assertThat(result.rejected().get(0).reason())
                    .contains("앞자리 0");
        }
    }

    /**
     * 이 테스트는 '버그'가 아니라 '알고 있는 한계'를 문서로 고정한 것이다.
     *
     * 30행 "012-123-4567" 은 하이픈을 지우면 0121234567 로 10자리이고
     * 0으로 시작하므로 내 규칙을 통과한다. 휴대폰번호로는 어색한 형태다.
     *
     * 막지 않은 이유: 샘플의 번호가 전부 012 로 시작하는 가짜 번호라서
     * 통신사 앞자리(010/011...) 정규식을 넣으면 파일 40행이 전부 반려된다.
     * "이 파일이 동작해야 한다"는 요구사항과 정면으로 충돌한다.
     *
     * 그래서 통과시키는 쪽을 택했고, 그 선택을 감춰두는 대신 테스트로 드러냈다.
     * 나중에 실제 번호 규칙이 정해지면 이 테스트가 먼저 빨간불이 되어
     * "여기 정책이 있었다"고 알려준다.
     */
    @Test
    @DisplayName("알려진 한계: 10자리 유선번호는 현재 규칙을 통과한다")
    void tenDigitLandlineCurrentlyPasses() {
        assertThat(PhoneNormalizer.normalize("012-123-4567")).isEqualTo("0121234567");
    }
}