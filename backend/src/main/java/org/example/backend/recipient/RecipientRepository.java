package org.example.backend.recipient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {

    List<Recipient> findByPhoneIn(Collection<String> phones);

    Page<Recipient> findByGrade(Grade grade, Pageable pageable);
}