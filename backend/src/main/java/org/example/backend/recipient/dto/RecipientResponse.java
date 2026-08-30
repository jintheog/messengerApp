package org.example.backend.recipient.dto;

import org.example.backend.common.PhoneMasker;
import org.example.backend.recipient.Grade;
import org.example.backend.recipient.Recipient;

import java.time.LocalDateTime;

/**
 * 수신자 하나를 화면에 내려줄 모양.
 *
 * ── 엔티티를 그대로 반환하지 않는 이유 ──
 * 1. 마스킹. Recipient 를 그대로 JSON 으로 만들면 phone 필드가 평문으로 나간다.
 *    요구사항 5번을 어기는 데다, 어긴 사실이 눈에 보이지 않아 더 위험하다.
 * 2. 엔티티에 필드를 추가하면 API 응답이 조용히 바뀐다. 내부 구조 변경이
 *    외부 계약을 깨는 것이라, 프론트가 예고 없이 망가질 수 있다.
 * 3. 지연 로딩(LAZY) 연관관계가 있으면 JSON 변환 중에 추가 쿼리가 나가거나
 *    예외가 터진다. Recipient 는 지금 연관관계가 없지만 언젠가 생긴다.
 *
 * ── 필드 이름을 phone 이 아니라 maskedPhone 으로 한 이유 ──
 * 이름 자체가 "여기 들어오는 값은 이미 가려진 것"이라고 알려준다.
 * phone 이라고 두면 나중에 누군가 실수로 원본을 대입해도 아무도 눈치채지 못한다.
 * 리뷰어가 maskedPhone 에 raw 값을 넣는 코드를 보면 바로 이상하다고 느낀다.
 * 타입으로는 막을 수 없으니 이름으로 막는다.
 */
public record RecipientResponse(
        Long id,
        String name,
        String maskedPhone,
        Grade grade,
        LocalDateTime createdAt
) {
    /**
     * ── static 팩토리 메서드 ──
     * 엔티티를 DTO 로 바꾸는 코드를 여기 한 곳에 모은다.
     * 변환을 서비스마다 흩어놓으면, 한 곳에서 마스킹을 빼먹는 순간
     * 그 API 만 평문을 내보내고 아무도 모른다.
     * 변환 경로를 하나로 만들어서 마스킹을 빠뜨릴 수 없게 하는 것이 목적이다.
     *
     * static 인 이유: RecipientResponse 객체가 아직 없는 상태에서 호출하므로
     * 인스턴스 메서드일 수 없다. 클래스 이름으로 바로 부른다.
     *
     * 이름을 of 나 from 으로 쓰는 게 자바 관례다. from 은 "다른 타입 하나를
     * 받아서 변환한다"는 뜻으로 쓰인다.
     */
    public static RecipientResponse from(Recipient recipient) {
        return new RecipientResponse(
                recipient.getId(),
                recipient.getName(),
                // 여기가 유일한 마스킹 지점이다.
                PhoneMasker.mask(recipient.getPhone()),
                recipient.getGrade(),
                recipient.getCreatedAt()
        );
    }
}