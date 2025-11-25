package br.edu.puc.fitjourneyai.domain.entity;

import br.edu.puc.fitjourneyai.domain.enums.MeasurementType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "measurements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Measurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40)
    private MeasurementType tipo;

    @Column(name = "valor", nullable = false)
    private Double valor;

    @Column(name = "data_registro", nullable = false)
    private LocalDateTime dataRegistro;
}
