package com.atlas.backend_atlas.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Set;

/** Código temporário gerado por um PCD autenticado. Guarda só o HMAC do código. */
@Entity
@Table(name = "codigo_vinculacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodigoVinculacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_hash", nullable = false, unique = true, length = 64)
    private String codigoHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pcd_id", nullable = false)
    private User pcd;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 15)
    private StatusCodigo status;

    /** Permissões que o PCD escolheu compartilhar com quem usar este código. */
    @Convert(converter = PermissaoSetConverter.class)
    @Column(nullable = false, length = 200)
    private Set<Permissao> permissoes;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "utilizado_em")
    private LocalDateTime utilizadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilizado_por")
    private User utilizadoPor;

    @Column(name = "revogado_em")
    private LocalDateTime revogadoEm;
}
