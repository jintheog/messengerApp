package org.example.backend.recipient;

// ── Apache Commons CSV 라이브러리 ───────────────────────────────
// 직접 쉼표로 자르지 않고 이 라이브러리를 쓰는 이유는 샘플에
// "고, 은지" 처럼 따옴표로 감싼 쉼표 포함 이름이 있기 때문이다.
// line.split(",") 로는 이 행이 4개 컬럼으로 쪼개져서 망가진다.
import org.apache.commons.csv.CSVFormat;   // 파싱 규칙(헤더 유무, 공백 처리 등)을 담는 설정 객체
import org.apache.commons.csv.CSVParser;   // 실제로 읽어나가는 객체
import org.apache.commons.csv.CSVRecord;   // 읽어낸 한 줄(한 행)

// ── 자바 표준 라이브러리 ────────────────────────────────────────
import java.io.IOException;                 // 파일/스트림 읽기 실패 시의 예외
import java.io.InputStream;                 // 업로드된 파일 내용이 흘러 들어오는 통로
import java.io.StringReader;                // 문자열을 "읽을 수 있는 대상"으로 감싸주는 도구
import java.nio.charset.StandardCharsets;   // UTF-8 같은 문자 인코딩 상수 모음
import java.util.ArrayList;                 // 순서가 있는 목록 (기본 선택)
import java.util.HashSet;                   // 중복을 허용하지 않는 집합
import java.util.List;                      // 목록의 '타입'. 실제 구현은 ArrayList
import java.util.Set;                       // 집합의 '타입'. 실제 구현은 HashSet

/**
 * 업로드된 CSV 파일을 읽어서 저장 가능한 수신자와 걸러낸 행으로 분류한다.
 *
 * 중요: 이 클래스는 DB를 전혀 모른다. Repository도 스프링도 쓰지 않는다.
 * 덕분에 테스트에서 MySQL을 켜지 않고도 1초 안에 검증할 수 있고,
 * "파일을 해석하는 책임"과 "저장하는 책임"이 분리된다.
 * 이미 DB에 있는 번호를 걸러내는 일은 서비스 계층이 담당한다.
 */
public class RecipientCsvParser {

    /**
     * @param inputStream 업로드된 파일의 내용
     * @return 분류 결과 (저장 후보 / 파일 내 중복 / 거절 / 경고)
     * @throws IOException 파일을 읽는 것 자체가 실패한 경우
     */
    public CsvParseResult parse(InputStream inputStream) throws IOException {

        // ── [1] 파일 내용을 문자열로 읽어들인다 ──────────────────────
        // InputStream 은 "바이트가 흘러오는 통로"다. 그 자체로는 글자가 아니라
        // 숫자(바이트) 덩어리이므로, 어떤 규칙으로 글자로 해석할지 알려줘야 한다.
        // 그 규칙이 문자 인코딩이고, 여기서 UTF_8 을 지정했다.
        //
        // 만약 인코딩을 지정하지 않으면 실행하는 컴퓨터의 기본값을 따라가는데,
        // 윈도우는 기본이 UTF-8이 아니라서 한글 이름이 전부 깨질 수 있다.
        // 그래서 이런 코드는 항상 인코딩을 명시해야 한다.
        //
        // 한계: 파일 전체를 메모리에 올린다. 40행이면 문제없지만 수십만 행이면
        // 메모리를 다 쓴다. 그때는 한 줄씩 흘려 읽는 방식으로 바꿔야 한다.
        String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        // ── [2] BOM 제거 ────────────────────────────────────────
        // BOM(Byte Order Mark)은 "이 파일은 UTF-8입니다"를 표시하려고 파일 맨 앞에
        // 붙는 보이지 않는 글자다. 엑셀에서 CSV로 저장하면 자주 붙는다.
        // \uFEFF 는 그 글자의 코드값이다.
        //
        // 이게 남아 있으면 첫 번째 컬럼 이름이 "이름"이 아니라 "(보이지 않는 글자)이름"이
        // 되어버려서, 눈으로는 똑같아 보이는데 비교가 안 되는 황당한 상황이 생긴다.
        // 실무에서 자주 터지는 문제라 미리 잘라낸다.
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);   // 맨 앞 한 글자를 버린 나머지
        }

        // ── [3] 파싱 규칙 설정 ──────────────────────────────────
        CSVFormat format = CSVFormat.DEFAULT.builder()
                // 첫 번째 줄을 데이터가 아닌 헤더(제목 줄)로 취급한다
                .setHeader()
                // 그 헤더 줄은 데이터로 세지 않고 건너뛴다
                .setSkipHeaderRecord(true)
                // 빈 줄은 무시한다. 샘플 파일 맨 끝에 빈 줄이 하나 있는데,
                // 이걸 데이터로 세면 "1건 실패"로 잡혀서 없는 문제를 찾게 된다
                .setIgnoreEmptyLines(true)
                // 각 칸의 앞뒤 공백을 잘라낸다.
                // 샘플의 "남기태, 012-6644-2288 ,일반" 같은 행을 처리해준다
                .setTrim(true)
                .get();   // 설정 완성. 버전에 따라 build() 인 경우도 있다

        // ── [4] 결과를 담을 통들을 준비한다 ──────────────────────
        // 왼쪽은 List(타입), 오른쪽은 ArrayList(실제 구현)로 쓰는 게 관례다.
        // 나중에 다른 구현으로 바꿔도 이 코드를 안 고쳐도 되게 하는 습관이다.
        List<ParsedRecipient> recipients = new ArrayList<>();        // 저장 후보
        List<RowIssue> duplicatedInFile = new ArrayList<>();   // 파일 내 중복
        List<RowIssue> rejected = new ArrayList<>();           // 거절
        List<RowIssue> warnings = new ArrayList<>();           // 저장했지만 알려야 할 것

        // 이미 등장한 번호를 기억하는 집합.
        // Set 은 같은 값을 두 번 담지 않는 성질이 있어서 중복 검사에 딱 맞다.
        // List 로 하면 매번 목록 전체를 훑어야 하는데(느림), Set 은 즉시 판정한다.
        Set<String> seenPhones = new HashSet<>();

        int totalRows = 0;   // 헤더와 빈 줄을 뺀 데이터 행 수

        // ── [5] try-with-resources ──────────────────────────────
        // try 괄호 안에서 만든 객체는 블록이 끝나면 자동으로 닫힌다(close()).
        // 예외가 터져서 중간에 빠져나가도 반드시 닫아준다.
        // 파일이나 네트워크를 다룰 때 닫는 걸 잊으면 자원이 계속 붙잡혀 있으므로
        // 이 문법을 쓰는 게 안전하다.
        try (CSVParser parser = format.parse(new StringReader(content))) {

            // parser 는 한 줄씩 꺼낼 수 있는 대상이라 for 문에 바로 넣을 수 있다.
            // "parser 안의 각 CSVRecord 를 row 라는 이름으로 하나씩 꺼내라"는 뜻.
            for (CSVRecord row : parser) {

                totalRows++;   // 헤더는 이미 건너뛴 상태이므로 데이터 행만 센다

                // ── 행 번호 계산 ──────────────────────────────
                // getRecordNumber() 는 헤더를 뺀 데이터 순번이라 첫 데이터 행이 1이다.
                // 그런데 담당자가 엑셀로 파일을 열면 헤더가 1행이므로,
                // +1 을 해서 "파일에서 눈에 보이는 줄 번호"로 맞춰준다.
                // 사유만 알려주고 위치를 안 알려주면 40줄을 눈으로 훑어야 한다.
                //
                // (int) 는 형변환이다. getRecordNumber() 는 long(더 큰 정수) 타입인데
                // RowIssue 가 int 를 받으므로 변환해서 넘긴다.
                int line = (int) row.getRecordNumber() + 1;

                // ── [6] 행 하나를 감싸는 try-catch ─────────────
                // 이 안쪽 try 가 "부분 저장"의 정체다.
                // 한 행이 규칙을 어겨 예외를 던져도 여기서 잡아 사유만 기록하고
                // 다음 행으로 넘어간다. 그래서 파일 전체가 실패하는 일이 없다.
                //
                // 만약 이 try 가 없으면 이상한 행 하나 때문에 40건이 통째로 날아가고,
                // "이 샘플 파일을 그대로 업로드했을 때 앱이 동작해야 한다"는
                // 요구사항을 정면으로 위반하게 된다.
                //
                // catch 에서 RuntimeException 이 아니라 InvalidRowException 만 잡는 것도
                // 의도적이다. 넓게 잡으면 우리가 만든 실제 버그(예: NullPointerException)까지
                // "거절된 행"으로 위장되어 영원히 숨는다.
                try {
                    // 컬럼이 3개보다 적으면 이름/번호/등급 중 하나를 읽을 수 없다.
                    // 확인 없이 get(2) 를 호출하면 예외 메시지가 알아보기 어려워지므로
                    // 먼저 우리 말로 사유를 만들어 던진다.
                    if (row.size() < 3) {
                        throw new InvalidRowException("컬럼이 3개가 아닙니다");
                    }

                    // 헤더 이름("이름", "휴대폰번호") 대신 위치(0,1,2)로 꺼낸다.
                    // 평가자가 헤더 문구를 조금 다르게 쓴 파일을 올려도 동작하도록
                    // 위치 기준이 더 관대하다.
                    String name = row.get(0);

                    // isBlank() 는 빈 문자열과 공백만 있는 경우를 모두 잡는다.
                    // 이름 없는 행을 거절하는 이유: 번호는 유효해서 발송은 가능하지만,
                    // 담당자가 "이 사람에게 보내도 되는가"를 판단할 수 없다.
                    // 이 앱은 관리 도구이고 담당자의 검토가 핵심 기능이다.
                    if (name.isBlank()) {
                        throw new InvalidRowException("이름이 없습니다");
                    }

                    // 하이픈/공백/+82 제거와 자릿수 검사를 전부 담당한다.
                    // 규칙에 맞지 않으면 여기서 InvalidRowException 이 던져지고
                    // 위 catch 로 잡힌다.
                    String phone = PhoneNormalizer.normalize(row.get(1));

                    // ── 중복 판정 ────────────────────────────
                    // Set.add() 는 새로 넣었으면 true, 이미 있던 값이면 false 를 준다.
                    // 그래서 "본 적 있는지 확인"과 "기록"이 이 한 줄로 끝난다.
                    //
                    // 먼저 들어온 번호가 집합에 남으므로 "먼저 온 행이 이긴다"가
                    // 자동으로 구현된다. 파일 순서에 의존하는 임의적 규칙이지만,
                    // 같은 번호로 문자를 두 번 보내는 것보다는 한 번이 안전하다는 판단이다.
                    //
                    // continue 는 이 행 처리를 여기서 끝내고 다음 행으로 넘어가라는 뜻이다.
                    if (!seenPhones.add(phone)) {
                        duplicatedInFile.add(new RowIssue(line, "파일 안에서 중복된 번호입니다"));
                        continue;
                    }

                    // 등급 문자열을 4개 ENUM 중 하나로 정규화한다.
                    // VIP / vip / V.I.P 를 각각 다른 값으로 저장하면
                    // 화면 필터 목록에 같은 등급이 세 번 뜨고,
                    // VIP 로 필터링했을 때 vip 데이터가 안 나오는 버그가 된다.
                    Grade grade = Grade.from(row.get(2));

                    // 등급을 해석할 수 없었던 경우. 행은 살리고 사실만 알려준다.
                    // 이름과 번호가 멀쩡한데 등급 하나 때문에 데이터를 버릴 이유가 없다.
                    // 거절이 아니라 '경고'인 이유: 저장은 됐기 때문이다.
                    if (grade == Grade.UNKNOWN) {
                        warnings.add(new RowIssue(line, "고객등급을 알 수 없어 '미상'으로 저장했습니다"));
                    }

                    // 객체만 만들어 목록에 담는다. 아직 DB 저장이 아니다.
                    // 저장은 서비스 계층이 기존 번호를 걸러낸 뒤에 한 번에 처리한다.
                    recipients.add(new ParsedRecipient(line, new Recipient(name, phone, grade)));

                } catch (InvalidRowException e) {
                    // e.getMessage() 로 위에서 던질 때 넣은 사유 문장을 꺼낸다.
                    // RowIssue 에 번호나 이름을 담지 않는 것이 의도다.
                    // 업로드 결과도 화면에 표시되므로, 원본 번호를 담으면
                    // "화면에 노출되는 번호는 마스킹" 요구사항을 위반한다.
                    rejected.add(new RowIssue(line, e.getMessage()));
                }
            }
        }

        // 다섯 값을 하나로 묶어 반환한다.
        // 자바 메서드는 값을 하나만 반환할 수 있고, 파일을 다시 읽는 낭비를
        // 피하려면 한 번 읽고 모든 결과를 함께 넘겨야 한다.
        return new CsvParseResult(totalRows, recipients, duplicatedInFile, rejected, warnings);
    }
}