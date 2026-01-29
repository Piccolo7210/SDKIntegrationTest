package org.neurotecfinger.model;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import javax.persistence.*;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Getter
@Setter
@Entity
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class FingerprintEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;


    private String fingerType;


    @Lob
    @Type(type = "org.hibernate.type.TextType") // <--- ADD THIS
    private String bmpBase64;

    private Integer quality;

    @Lob
    @Type(type = "org.hibernate.type.TextType") // <--- ADD THIS
    private String wsqData;

    // The mathematical template extracted by Neurotec (Crucial for matching)
    @Lob
    @Type(type = "org.hibernate.type.TextType") // <--- ADD THIS
    @Column(name = "native_template", nullable = false)
    private String nativeTemplate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

}