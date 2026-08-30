package org.example.backend.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이징 결과를 프론트에 내려줄 공통 모양.
 *
 * ── 스프링의 Page 를 그대로 반환하지 않는 이유 ──
 * Page 객체를 그대로 돌려주면 JSON 이 이렇게 나온다.
 *   { "content":[...], "pageable":{...}, "sort":{...}, "last":true, "first":true, ... }
 * 필요 없는 필드가 잔뜩 붙고, 무엇보다 이 구조는 스프링 내부 구현이라
 * 스프링 버전을 올리면 모양이 바뀔 수 있다. 실제로 Spring Boot 3.3 부터
 * PageImpl 을 직접 직렬화하면 경고가 뜬다. 프론트가 스프링 버전에 묶이는 셈이다.
 *
 * 필요한 값만 골라 우리가 정의한 모양으로 내려주면, 스프링을 바꾸든
 * MyBatis 로 갈아타든 API 계약은 그대로 유지된다.
 *
 * ── 제네릭 <T> ──
 * 이 클래스는 수신자 목록에도, 발송 이력 목록에도 쓰인다.
 * 담기는 내용은 다르지만 페이징 정보(몇 페이지, 총 몇 건)는 똑같다.
 * <T> 는 "담길 타입은 쓰는 쪽에서 정한다"는 뜻이다.
 * PageResponse<RecipientResponse> 처럼 쓰면 T 가 RecipientResponse 가 된다.
 *
 * 이게 없으면 RecipientPageResponse, MessagePageResponse 처럼 내용만 다른
 * 똑같은 클래스를 계속 만들어야 한다.
 */
public record PageResponse<T>(
        // 이번 페이지에 담긴 실제 데이터
        List<T> content,

        // 현재 페이지 번호. 0부터 시작한다(스프링 기본).
        // 화면에 "1페이지"로 보이려면 프론트에서 +1 해야 한다.
        int page,

        // 한 페이지 크기
        int size,

        // 필터를 적용한 전체 건수.
        // long 인 이유: 건수가 int 최대값(약 21억)을 넘을 수 있다는 전제다.
        // 이 앱에서는 넘지 않지만 Page 가 long 으로 주므로 그대로 맞춘다.
        long totalElements,

        // 전체 페이지 수. 페이지 번호 버튼을 그리는 데 쓴다.
        int totalPages,

        // 다음 페이지가 있는지.
        // totalPages 로 프론트가 계산할 수도 있지만, 그 계산을 프론트마다
        // 다시 하다 보면 경계값(마지막 페이지)에서 실수가 난다.
        // 서버가 한 번 계산해서 내려주는 편이 안전하다.
        boolean hasNext
) {
    /**
     * <T> 를 반환 타입 앞에 또 쓰는 이유:
     * static 메서드는 클래스의 T 를 쓸 수 없다. 클래스의 T 는 객체가 만들어질 때
     * 정해지는데, static 메서드는 객체 없이 호출되기 때문이다.
     * 그래서 이 메서드만의 T 를 따로 선언한다.
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}