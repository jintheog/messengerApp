package org.example.backend.recipient;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
@Entity
@Table(name = "recipient",
        uniqueConstraints = @UniqueConstraint(name = "uk_recipient_phone", columnNames = "phone"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Grade grade;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Recipient(String name, String phone, Grade grade) {
        this.name = name;
        this.phone = phone;
        this.grade = grade;
        this.createdAt = LocalDateTime.now();
    }
}