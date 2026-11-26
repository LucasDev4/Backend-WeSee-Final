package com.atlas.backend_atlas.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Set;

/** Relação autorizada entre um RESPONSAVEL e um PCD_VISUAL. */
@Entity
@Table(name = "vinculo_responsavel")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VinculoResponsavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id", nullable = false)
    private User responsavel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pcd_id", nullable = false)
    private User pcd;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15)
    private StatusVinculo status;

    @Convert(converter = PermissaoSetConverter.class)
    @Column(nullable = false, length = 200)
    private Set<Permissao> permissoes;

    /** Código (gerado pelo PCD) que originou o vínculo: registra o consentimento. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "codigo_id")
    private CodigoVinculacao codigo;

    /** Quem iniciou o vínculo (o responsável que usou o código). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false)
    private User criadoPor;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @Column(name = "autorizado_em")
    private LocalDateTime autorizadoEm;

    @Column(name = "revogado_em")
    private LocalDateTime revogadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revogado_por")
    private User revogadoPor;

    @PrePersist
    void aoCriar() {
        LocalDateTime agora = LocalDateTime.now();
        if (criadoEm == null) criadoEm = agora;
        if (atualizadoEm == null) atualizadoEm = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        atualizadoEm = LocalDateTime.now();
    }
}
