package org.example.backend.recipient;

import org.example.backend.recipient.dto.UploadResultResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.backend.common.dto.PageResponse;
import org.example.backend.recipient.dto.RecipientResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 수신자 관련 업무 규칙을 담는 계층.
 *
 * ── 컨트롤러와 서비스를 나눈 이유 ──
 * 컨트롤러는 "HTTP 요청을 받아서 자바 값으로 바꾸고, 결과를 JSON으로 돌려준다"만
 * 한다. 즉 웹이라는 통로에 대한 일이다.
 * 서비스는 "무엇을 저장하고 무엇을 걸러낼지"를 정한다. 즉 업무 규칙이다.
 *
 * 이렇게 나누면 나중에 같은 기능을 배치 작업이나 CLI로 실행해야 할 때
 * 서비스를 그대로 재사용할 수 있다. 컨트롤러에 다 몰아넣으면
 * HTTP 요청 없이는 아무것도 실행할 수 없는 코드가 된다.
 *
 * ── @Service 어노테이션 ──
 * 스프링에게 "이 클래스의 객체를 하나 만들어서 관리해달라"고 알린다.
 * 그러면 컨트롤러가 이 객체를 직접 new 하지 않고 받아 쓸 수 있다.
 * 기능은 @Component 와 같지만, 이름으로 "여기가 업무 규칙 계층"임을 알린다.
 */
@Service
public class RecipientService {

    // final: 생성자에서 한 번 대입한 뒤 절대 바뀌지 않는다는 표시.
    // 실행 중에 다른 Repository 로 갈아치우는 일이 없어야 하므로 final 이 맞다.
    private final RecipientRepository recipientRepository;

    // ── 파서는 왜 스프링 빈이 아닌가 ──
    // RecipientCsvParser 는 기억하는 상태도, 의존하는 다른 객체도 없다.
    // 스프링이 관리해줘서 얻는 이득(의존성 주입, 프록시)이 하나도 없으므로
    // 그냥 직접 만들어 쓴다.
    // 의도도 있다. 이 클래스에 @Component 를 붙이면 "스프링 없이는 못 쓰는 클래스"로
    // 보이는데, 스프링과 DB를 모르는 순수 클래스로 유지하는 것이 이 설계의 핵심이다.
    // 테스트에서 new RecipientCsvParser() 로 바로 쓰는 것도 그 덕분이다.
    private final RecipientCsvParser csvParser = new RecipientCsvParser();

    // ── 생성자 주입 ──
    // 스프링이 이 클래스의 객체를 만들 때, 생성자에 필요한 타입의 빈을 찾아 넣어준다.
    // @Autowired 를 필드에 붙이는 방식도 있지만 생성자 주입이 표준이다.
    // 이유: (1) final 로 만들 수 있어 불변이 보장되고
    //       (2) 필요한 의존이 생성자 목록에 다 드러나서 숨은 의존이 없고
    //       (3) 스프링 없이 new RecipientService(가짜Repository) 로 테스트할 수 있다.
    // 생성자가 하나뿐이면 @Autowired 를 생략해도 스프링이 알아서 쓴다.
    public RecipientService(RecipientRepository recipientRepository) {
        this.recipientRepository = recipientRepository;
    }

    /**
     * CSV를 파싱하고, 이미 등록된 번호를 걸러낸 뒤 저장한다.
     *
     * ── 파라미터가 MultipartFile 이 아니라 InputStream 인 이유 ──
     * MultipartFile 은 스프링 웹의 타입이다. 서비스가 그것을 받으면
     * "웹 요청이 아니면 이 서비스를 호출할 수 없는" 상태가 된다.
     * InputStream 은 자바 표준이라 파일, 네트워크, 문자열 어디서 와도 된다.
     * 웹에 대한 지식은 컨트롤러에만 두는 것이 계층을 나눈 목적에 맞다.
     *
     * ── @Transactional ──
     * 이 메서드 전체를 하나의 DB 작업 단위로 묶는다.
     * 중간에 예외가 터지면 그때까지 저장한 것을 되돌린다(롤백).
     *
     * 여기서 묶는 이유는 조회와 저장이 짝을 이루기 때문이다.
     * "이미 있는 번호를 조회" 한 다음 "없는 것만 저장" 하는데, 그 사이에
     * 다른 요청이 같은 번호를 저장해버리면 조회 결과가 낡은 정보가 된다.
     * 트랜잭션이 이 창을 좁혀주고, 그래도 뚫리면 DB의 UNIQUE 제약이 막는다.
     *
     * 알고 있는 한계: 파싱까지 트랜잭션 안에서 하므로 파일을 읽는 동안
     * DB 커넥션을 붙잡고 있다. 40행이면 무의미하지만 대용량 파일에서는
     * 파싱을 트랜잭션 밖으로 빼야 한다.
     */
    @Transactional
    public UploadResultResponse upload(InputStream inputStream) throws IOException {

        // ── [1] 파일을 해석한다 (DB 접근 없음) ──────────────────
        CsvParseResult parsed = csvParser.parse(inputStream);

        // ── [2] 저장 후보들의 번호만 뽑아낸다 ──────────────────
        // stream(): 목록을 "흘려보내며 가공하는" 통로로 바꾼다.
        // map(...): 각 원소를 다른 값으로 바꾼다. 여기서는 ParsedRecipient -> 번호 문자열.
        // toList(): 다시 목록으로 모은다. (Java 16+ 문법. 예전에는 collect(Collectors.toList()))
        //
        // p -> p.recipient().getPhone() 는 람다다.
        // "ParsedRecipient 하나를 p 라고 부르고, 그것의 번호를 꺼내라"는 뜻이다.
        List<String> phones = parsed.recipients().stream()
                .map(p -> p.recipient().getPhone())
                .toList();

        // ── [3] 그 번호들 중 이미 DB에 있는 것을 한 번에 조회한다 ──
        // findByPhoneIn 은 SQL 의 IN 절이 된다.
        //   SELECT * FROM recipient WHERE phone IN ('011...', '012...', ...)
        //
        // 왜 한 번에 조회하는가:
        // 32건을 하나씩 existsByPhone 으로 확인하면 쿼리가 32번 나간다.
        // 이걸 N+1 문제라고 부른다. 건수가 늘어나면 그대로 느려진다.
        // IN 절로 묶으면 쿼리 한 번으로 끝난다.
        //
        // Set 으로 모으는 이유: 아래에서 "이 번호가 있었나?"를 32번 물어보는데
        // List.contains() 는 매번 목록 전체를 훑는다(느림).
        // Set.contains() 는 즉시 판정한다.
        Set<String> existingPhones = recipientRepository.findByPhoneIn(phones).stream()
                .map(Recipient::getPhone)
                .collect(Collectors.toSet());

        // ── [4] 저장할 것과 건너뛸 것으로 나눈다 ────────────────
        List<Recipient> toSave = new ArrayList<>();
        List<RowIssue> alreadyRegistered = new ArrayList<>();


        // 실제로 저장된 행 번호를 기억한다. 아래 [6]에서 경고를 걸러낼 때 쓴다.
        Set<Integer> savedLines = new HashSet<>();

        for (ParsedRecipient p : parsed.recipients()) {
            if (existingPhones.contains(p.recipient().getPhone())) {
                // 여기가 ParsedRecipient 를 만든 이유다. line 이 없으면
                // "3건이 이미 있었습니다"까지만 말할 수 있다.
                //
                // 사유 문장을 "중복"이 아니라 "이미 등록"으로 쓴 것도 의도다.
                // 담당자가 해야 할 행동이 다르다. 파일 내 중복은 파일을 고쳐야 하지만,
                // 이미 등록된 번호는 아무것도 안 해도 되는 정상 상황이다.
                alreadyRegistered.add(new RowIssue(p.line(), "이미 등록된 번호라 건너뛰었습니다"));
            } else {
                toSave.add(p.recipient());
                savedLines.add(p.line());
            }
        }

        // ── [5] 한 번에 저장한다 ───────────────────────────────
        // save() 를 반복 호출하는 대신 saveAll() 을 쓴다.
        // 실제로 나가는 INSERT 문 개수는 같지만(엔티티가 IDENTITY 전략이라
        // 배치로 묶이지 않는다), 트랜잭션 처리와 flush 시점이 한 번에 정리된다.
        //
        // 알고 있는 한계: IDENTITY 전략은 INSERT 를 보내야 id 를 알 수 있어서
        // JPA 가 여러 건을 하나의 INSERT 로 묶지 못한다. 수만 건을 넣어야 한다면
        // JdbcTemplate 으로 직접 배치 INSERT 를 하는 편이 훨씬 빠르다.
        recipientRepository.saveAll(toSave);


        // ── [6] 경고를 '실제로 저장된 행'으로 좁힌다 ─────────────
        // 경고의 정의는 "저장은 했지만 담당자가 알아야 할 것"이다.
        // 그런데 경고를 만드는 것은 파서이고, 파서는 DB를 모르므로
        // 이 행이 나중에 "이미 등록된 번호"로 걸러질 줄 모른 채 경고를 남긴다.
        //
        // 걸러내지 않으면 같은 파일을 두 번 올렸을 때
        // savedCount 는 0인데 "미상으로 저장했습니다"라는 경고가 남는다.
        // 저장하지 않았는데 저장했다고 말하는 응답이 되고,
        // 같은 행이 alreadyRegistered 와 warnings 에 모순되게 나온다.
        //
        // 계층을 나눈 대가다. 파서를 DB에서 분리한 이득이 크므로 구조는 유지하고,
        // 두 계층의 정보가 합쳐지는 이 지점에서 정합성을 맞춘다.
        List<RowIssue> warnings = parsed.warnings().stream()
                .filter(w -> savedLines.contains(w.line()))
                .toList();
        return new UploadResultResponse(
                parsed.totalRows(),
                toSave.size(),
                parsed.rejected(),
                parsed.duplicatedInFile(),
                alreadyRegistered,
                warnings  // parsed.warnings() 가 아니라 걸러낸 것을 넘긴다
        );
    }

    /**
     * 수신자 목록을 등급으로 걸러서 한 페이지 가져온다.
     *
     * ── @Transactional(readOnly = true) ──
     * 읽기 전용이라고 알려준다. 두 가지 이득이 있다.
     * 1. JPA 가 "이 데이터가 바뀌었는지" 검사하는 작업을 생략해서 조금 빠르다.
     * 2. 실수로 여기서 데이터를 고치는 코드가 들어가면 예외로 막힌다.
     *    조회 메서드에서 데이터가 바뀌는 사고를 문법으로 예방한다.
     *
     * ── grade 가 null 이면 전체 조회 ──
     * "필터 없음"을 별도 값(예: Grade.ALL)으로 만들지 않았다.
     * ALL 을 enum 에 넣으면 등급이 5개가 되어 저장 가능한 값처럼 보이고,
     * DB의 check 제약과 화면 선택 목록에도 섞여 들어간다.
     * 없는 상태는 null 로 표현하는 게 맞다.
     */
    @Transactional(readOnly = true)
    public PageResponse<RecipientResponse> findRecipients(Grade grade, Pageable pageable) {

        // 삼항 연산자: 조건 ? 참일때값 : 거짓일때값
        // if-else 로 써도 같지만, "둘 중 하나를 고른다"가 한눈에 보인다.
        //
        // findAll(pageable) 은 JpaRepository 가 이미 갖고 있어서 안 만들어도 된다.
        Page<Recipient> page = (grade == null)
                ? recipientRepository.findAll(pageable)
                : recipientRepository.findByGrade(grade, pageable);

        // ── page.map(...) ──
        // 페이징 정보(총 건수, 페이지 번호)는 그대로 두고 내용만 변환한다.
        // page.getContent() 를 꺼내 새 목록을 만들면 총 건수를 다시 채워야 하는데,
        // map 은 그 수고를 없애준다.
        //
        // RecipientResponse::from 은 메서드 참조다.
        // r -> RecipientResponse.from(r) 과 같은 뜻이다.
        //
        // 이 한 줄이 마스킹이 적용되는 지점이다. 엔티티를 그대로 반환하면
        // 평문 번호가 나간다.
        return PageResponse.from(page.map(RecipientResponse::from));
    }
}