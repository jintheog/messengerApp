package org.example.backend.recipient.dto;

import org.example.backend.recipient.RowIssue;

import java.util.List;

/**
 * 업로드 결과를 프론트에 돌려주는 응답 모양.
 *
 * 과제 요구사항: "업로드 결과를 사용자에게 알려주세요.
 *                (몇 건 성공했는지, 문제가 있던 건은 어떻게 처리했는지)"
 *
 */
public record UploadResultResponse(
        // 헤더와 빈 줄을 뺀 전체 데이터 행 수. 아래 숫자들의 검산 기준이 된다.
        int totalRows,

        // 실제로 DB에 저장된 건수
        int savedCount,

        // 규칙 위반으로 저장하지 않은 행들
        List<RowIssue> rejected,

        // 파일 안에서 앞선 행과 번호가 겹쳐 건너뛴 행들
        List<RowIssue> duplicatedInFile,

        // 데이터는 정상이지만 DB에 이미 있던 번호라 건너뛴 행들
        List<RowIssue> alreadyRegistered,

        // 저장은 했지만 담당자가 알아야 할 점이 있는 행들 (등급 미상 등)
        List<RowIssue> warnings
) {
}