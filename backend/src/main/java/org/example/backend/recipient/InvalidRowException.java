package org.example.backend.recipient;
/**
 * CSV 한 행이 우리 규칙을 통과하지 못했을 때 던지는 예외.
 *
 * 파일 읽기 실패(IOException)나 프로그램 버그(NullPointerException)와
 * 구분해서 잡아내기 위해 별도 타입으로 만들었다.
 * 이 예외만 잡으면 "데이터가 잘못된 행"만 골라서 처리할 수 있다.
 */
public class InvalidRowException extends RuntimeException{

    /**
     * @param message 거절 사유. 담당자에게 그대로 보여줄 문장이므로
     *                "이름이 없습니다"처럼 사람이 읽을 수 있게 쓴다.
     */
    public InvalidRowException(String message) {
        // 부모(RuntimeException)에게 메시지를 넘겨 보관시킨다.
        // 이걸 호출해야 나중에 e.getMessage()로 사유를 꺼낼 수 있다.
        super(message); // super: 부모 클래스; 부모의 생성자를 호출해서 이 메시지를 넘긴다.
    }
}
