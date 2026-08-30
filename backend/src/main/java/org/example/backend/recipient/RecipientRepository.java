package org.example.backend.recipient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {

    // 업로드 시 이미 등록된 번호를 한 번에 조회한다. SQL 의 IN 절이 된다.
    List<Recipient> findByPhoneIn(List<String> phones);
    // 등급으로 걸러서 한 페이지만 가져온다.
    // 메서드 이름만으로 쿼리가 만들어지는 게 Spring Data JPA 의 기능이다.
    // findBy + Grade -> WHERE grade = ?
    // 반환 타입이 Page 이면 전체 건수를 세는 COUNT 쿼리도 함께 나간다.
    // 마지막 파라미터가 Pageable 이면 LIMIT / OFFSET / ORDER BY 가 붙는다.
    Page<Recipient> findByGrade(Grade grade, Pageable pageable);
}