package org.example.backend.recipient;
//문제가 있던 행의 위치와 사유

//record는 데이터를 담기만 하는 클래스를 한 줄로 쓰게 해주는 문법
/**record가 만들어 주는것:
* 필드 2개: int line, String reason (both private final)
 * 생성자: new RowIssue(32, "휴대폰 번호 자릿수가 ....")
 * 값을 꺼내는 메서드: issue.line(), issue.reason()
 * equals(), hashCode(): 두 객체의 값이 같으면 같은것으로 취급.
 * toString().: e.g. RowIssue[line=32, reason=휴대폰번호 자릿수가...] 같은 알아볼 수 있는 값을 출력
 *
 * Getter 이름이 다르다:
 * 기존 Class/Lombok: issue.getLine(), issue.getReason()
 * record: issue.line(), issue.reason() (prefix get이 없음)
 *
 * 불변(Immutable) 객체:
 * 필드에 Setter가 존재하지 않으며, 한 번 값이 들어가면 바꿀 수 없다
 * 따라서 엑셀 업로드 에러 내역, 조회 전용 DTO, API 응답 객체처럼 "값을 전달만 하고 변경할 필요가 없는 데이터"에 적합.
 * */
public record RowIssue(int line, String reason) { //
}
