package org.example.backend.recipient;

import org.example.backend.recipient.dto.UploadResultResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.example.backend.common.dto.PageResponse;
import org.example.backend.recipient.dto.RecipientResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;

import java.io.IOException;
import java.io.InputStream;

/**
 * 수신자 관련 HTTP 요청을 받는 입구.
 *
 * ── @RestController ──
 * @Controller + @ResponseBody 를 합친 것이다.
 * 메서드가 돌려준 객체를 스프링이 자동으로 JSON 으로 바꿔서 응답 본문에 넣는다.
 * 이게 없으면 반환값을 "보여줄 HTML 화면 이름"으로 해석한다.
 *
 * ── @RequestMapping("/api/recipients") ──
 * 이 클래스의 모든 메서드 주소 앞에 붙는 공통 경로다.
 * /api 를 붙인 이유: 나중에 프론트를 같은 서버에서 서비스할 때
 * 화면 주소와 데이터 주소가 섞이지 않게 하려는 관례다.
 */
@RestController
@RequestMapping("/api/recipients")
public class RecipientController {

    private final RecipientService recipientService;

    public RecipientController(RecipientService recipientService) {
        this.recipientService = recipientService;
    }

    /**
     * CSV 파일을 받아 수신자를 저장한다.
     *
     * ── POST 를 쓴 이유 ──
     * GET 은 "가져오기"이고 서버 상태를 바꾸지 않아야 한다.
     * 이 요청은 DB에 데이터를 만들므로 POST 가 맞다.
     * 브라우저와 중간 서버들이 GET 응답을 캐시하기도 해서, 상태를 바꾸는
     * 요청을 GET 으로 만들면 예상 못 한 동작이 생긴다.
     *
     * ── @RequestParam("file") MultipartFile ──
     * multipart/form-data 형식으로 올라온 파일을 받는다.
     * "file" 은 프론트에서 FormData 에 넣을 때 쓸 이름이고, 양쪽이 같아야 한다.
     * application.properties 의 multipart 최대 크기(10MB)가 여기에 적용된다.
     */
    @PostMapping("/upload")
    public UploadResultResponse upload(@RequestParam("file") MultipartFile file) throws IOException {

        // ── 빈 파일을 여기서 막는 이유 ──
        // 파일 선택을 안 하고 업로드를 누르는 실수는 실제로 자주 일어난다.
        // 그냥 통과시키면 파서가 "0행 처리했습니다"를 돌려주고,
        // 담당자는 파일이 잘못된 줄 알고 원본을 뒤진다.
        // "파일이 비어 있다"는 사실을 알려주는 게 훨씬 도움이 된다.
        //
        // 검증을 컨트롤러에 둔 이유: 이건 업무 규칙이 아니라 요청 형식 문제다.
        // 업무 규칙(어떤 행을 거절할지)은 서비스와 파서에 있다.
        if (file.isEmpty()) {
            // ResponseStatusException: 상태 코드와 메시지를 담아 던지면
            // 스프링이 그 상태 코드로 응답해준다.
            // 400 Bad Request 는 "요청이 잘못됐다"는 뜻이다. 서버 잘못이 아니므로
            // 500 이 아니어야 한다. 프론트가 이 코드를 보고 사용자에게 안내할 수 있다.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업로드할 파일이 없습니다");
        }

        // try-with-resources 로 스트림을 확실히 닫는다.
        // 임시 파일을 붙잡고 있으면 자원이 계속 점유된다.
        try (InputStream inputStream = file.getInputStream()) {
            return recipientService.upload(inputStream);
        }
    }

    /**
     * 수신자 목록을 조회한다.
     *   GET /api/recipients                      전체, 1페이지
     *   GET /api/recipients?grade=VIP            VIP 만
     *   GET /api/recipients?page=1&size=10       2페이지, 10개씩
     *
     * ── @RequestParam(required = false) Grade grade ──
     * 쿼리스트링의 "VIP" 같은 문자열을 스프링이 Grade enum 으로 바꿔준다.
     * required = false 라서 파라미터를 아예 안 보내면 null 이 들어오고,
     * 그러면 서비스가 전체를 조회한다.
     *
     * grade= 처럼 빈 값을 보내도 null 이 된다(스프링의 문자열-enum 변환기가
     * 빈 문자열을 null 로 처리한다). 프론트에서 "전체" 선택 시
     * 파라미터를 지우는 처리를 안 해도 동작한다.
     *
     * "VVIP" 처럼 없는 값을 보내면 스프링이 자동으로 400 Bad Request 를 낸다.
     * 오타를 조용히 무시하고 전체를 보여주는 것보다, 잘못됐다고 알려주는 게 낫다.
     *
     * ── Pageable ──
     * page, size, sort 쿼리 파라미터를 스프링이 알아서 담아준다.
     * 직접 int page, int size 를 받아 검증하는 코드를 안 써도 된다.
     *
     * ── @PageableDefault 의 sort 가 두 개인 이유 (중요) ──
     * 정렬 기준이 없는 페이징은 페이지 간 중복과 누락을 만든다.
     * DB는 ORDER BY 가 없으면 순서를 보장하지 않으므로, 1페이지에 나온 행이
     * 2페이지에 또 나오거나 아예 안 나올 수 있다.
     *
     * 그런데 createdAt 하나만으로는 부족하다. 업로드는 32건을 한 번에 저장하고
     * MySQL 의 datetime 은 기본 정밀도가 초 단위라, 32건의 createdAt 이
     * 전부 같은 값이 된다. 같은 값끼리는 순서가 다시 보장되지 않는다.
     *
     * 그래서 id 를 두 번째 기준으로 둔다. id 는 유일하고 증가하므로
     * 동시간 저장분의 순서까지 확정된다. 이 두 개를 묶으면
     * "최신순으로 보여주되 순서는 항상 같다"가 성립한다.
     *
     * 최신순(DESC)인 이유: 방금 올린 파일이 제대로 들어갔는지 확인하는 것이
     * 업로드 직후 담당자의 첫 행동이다.
     */
    @GetMapping
    public PageResponse<RecipientResponse> findRecipients(
            @RequestParam(required = false) Grade grade,
            @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return recipientService.findRecipients(grade, pageable);
    }
}