package org.example.backend.recipient;

import java.util.List;

/**
 * CSV 파일 하나를 파싱한 결과를 한 덩어리로 묶어 전달하는 그릇.
 *
 * 파서가 "저장할 수신자 목록"만 돌려주면 안 된다. 요구사항이
 * "몇 건 성공했고 문제가 있던 건은 어떻게 처리했는지" 알려달라고 했으므로,
 * 걸러낸 행의 위치와 사유도 함께 넘겨야 한다.
 * 자바 메서드는 값을 하나만 반환할 수 있어서 이렇게 하나로 묶는다.
 *
 * 샘플 파일 기준 기대값:
 *   totalRows 40 / recipients 32 / duplicatedInFile 2 / rejected 6 / warnings 1
 */
/*
자바 메서드는 값을 하나만 반환할 수 있음. 그런데 파서가 알려줘야 할 게 다섯 가지:
 저장할 수신자 목록, 중복으로 건너뛴 행, 거절한 행, 경고와 함께 저장한 행, 그리고 전체 행 수요.
 메서드를 5개로 나눠 각각 반환 하면 파일을 5번 읽어야 함. -> 하나로 묶어서 한번에 건내줌.
 */
public record CsvParseResult(
        // 헤더와 빈 줄을 뺀 데이터 행 수. 샘플은 40.
        int totalRows,

        // 검증을 통과하고 파일 내 중복까지 제거된, 저장 후보 수신자들.
        // 아직 DB에 저장된 게 아니라 "저장해도 되는 후보".
        // 이미 DB에 있는 번호를 걸러내는 일은 서비스 계층에서 한다.

        // Recipient 가 아니라 ParsedRecipient 를 담는 이유:
        // 서비스가 "이미 등록된 번호"를 걸러낼 때 몇 번째 행이었는지 알려주려면
        // 행 번호가 여기까지 따라와야 한다.
        List<ParsedRecipient> recipients,

        // 파일 안에서 앞선 행과 번호가 겹쳐 건너뛴 행들.
        // "이미 등록돼 있던 번호"와는 사용자에게 완전히 다른 정보라 따로 담는다.
        List<RowIssue> duplicatedInFile,

        // 규칙을 통과하지 못해 저장하지 않은 행들. 사유는 행마다 다르다.
        List<RowIssue> rejected,

        // 저장은 했지만 담당자가 알아야 할 점이 있는 행들.
        // 지금은 등급을 알 수 없어 '미상'으로 넣은 경우만 해당한다.
        List<RowIssue> warnings
) {
}