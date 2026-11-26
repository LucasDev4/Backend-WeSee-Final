package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.dto.Priority;
import com.atlas.backend_atlas.dto.Proximity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Regras de prioridade e vocabulário (pt-BR) dos objetos detectados.
 * <p>
 * Urgência para quem tem deficiência visual: pessoas, veículos, escadas e buracos (bueiro) vêm antes de obstáculos fixos
 * (poste, cone, lixeira...) e de elementos informativos (placa, faixa de pedestres). Classes "de contexto" (rua, prédio)
 * ocupam a imagem inteira e só entram na descrição da cena, nunca nos alertas. Classes desconhecidas recebem prioridade BAIXA.
 * <p>
 * A prioridade final sobe um nível se o objeto estiver muito próximo (risco imediato de colisão).
 * O catálogo cobre as 25 classes do modelo WeSee (best.pt, nomes em inglês) e nomes equivalentes em português / COCO.
 */
@Component
public class PriorityRules {

    private record ClassInfo(String label, String plural, boolean feminine, Priority basePriority, boolean context) {}

    private static final Map<String, ClassInfo> CATALOG = new HashMap<>();

    private static void add(ClassInfo info, String... keys) {
        for (String key : keys) CATALOG.put(key, info);
    }

    private static ClassInfo obj(String label, String plural, boolean feminine, Priority priority) {
        return new ClassInfo(label, plural, feminine, priority, false);
    }

    static {
        // --- pessoas e veículos (urgência alta) ---
        add(obj("Pessoa", "Pessoas", true, Priority.HIGH), "person", "pessoa", "pedestrian", "pedestre");
        add(obj("Veículo", "Veículos", false, Priority.HIGH), "vehicle", "veiculo", "veículo");
        add(obj("Carro", "Carros", false, Priority.HIGH), "car", "carro");
        add(obj("Ônibus", "Ônibus", false, Priority.HIGH), "bus", "onibus", "ônibus");
        add(obj("Caminhão", "Caminhões", false, Priority.HIGH), "truck", "caminhao", "caminhão");
        add(obj("Moto", "Motos", true, Priority.HIGH), "motorcycle", "moto", "motocicleta");
        add(obj("Bicicleta", "Bicicletas", true, Priority.HIGH), "bicycle", "bicicleta", "bike");
        // --- riscos no chão / desníveis ---
        add(obj("Escada", "Escadas", true, Priority.HIGH), "stairs", "escada", "escadas");
        add(obj("Bueiro", "Bueiros", false, Priority.HIGH), "manhole", "bueiro");
        // --- obstáculos ---
        add(obj("Cachorro", "Cachorros", false, Priority.MEDIUM), "dog", "cachorro", "cao", "cão");
        add(obj("Poste", "Postes", false, Priority.MEDIUM), "electrical pole", "poste");
        add(obj("Lixeira", "Lixeiras", true, Priority.MEDIUM), "dustbin", "lixeira");
        add(obj("Cone", "Cones", false, Priority.MEDIUM), "traffic cone", "cone");
        add(obj("Barril de sinalização", "Barris de sinalização", false, Priority.MEDIUM), "traffic barrel");
        add(obj("Hidrante", "Hidrantes", false, Priority.MEDIUM), "fire hydrant", "hidrante");
        add(obj("Grade de proteção", "Grades de proteção", true, Priority.MEDIUM), "guard rail", "grade");
        add(obj("Porta", "Portas", true, Priority.MEDIUM), "door", "porta");
        add(obj("Obstáculo", "Obstáculos", false, Priority.MEDIUM), "obstacle", "obstaculo", "obstáculo");
        // --- objetos de menor risco ---
        add(obj("Árvore", "Árvores", true, Priority.LOW), "tree", "arvore", "árvore");
        add(obj("Banco", "Bancos", false, Priority.LOW), "bench", "banco");
        add(obj("Cadeira", "Cadeiras", true, Priority.LOW), "chair", "cadeira");
        add(obj("Vaso de planta", "Vasos de planta", false, Priority.LOW), "plant pot", "vaso");
        add(obj("Caixa elétrica", "Caixas elétricas", true, Priority.LOW), "electrical box", "caixa eletrica", "caixa elétrica");
        add(obj("Bicicletário", "Bicicletários", false, Priority.LOW), "bicycle rack", "bicicletario", "bicicletário");
        // --- informativos ---
        add(obj("Placa de trânsito", "Placas de trânsito", true, Priority.LOW), "traffic sign", "placa");
        add(obj("Faixa de pedestres", "Faixas de pedestres", true, Priority.LOW), "pedestrian crosswalk", "faixa de pedestres");
        // --- contexto (ocupam a imagem toda; só descrevem a cena) ---
        add(new ClassInfo("Rua", "Ruas", true, Priority.LOW, true), "road", "rua");
        add(new ClassInfo("Prédio", "Prédios", false, Priority.LOW, true), "building", "predio", "prédio");
    }

    private static final ClassInfo DEFAULT_INFO = new ClassInfo(null, null, false, Priority.LOW, false);

    /** Rótulo em português (singular) para falar/mostrar. */
    public String label(String rawClass) {
        ClassInfo info = lookup(rawClass);
        return info.label() != null ? info.label() : capitalize(rawClass);
    }

    /** Rótulo em português no plural. */
    public String plural(String rawClass) {
        ClassInfo info = lookup(rawClass);
        return info.plural() != null ? info.plural() : label(rawClass);
    }

    /** Concordância de gênero (feminino/masculino) do rótulo, usada nas mensagens. */
    public boolean isFeminine(String rawClass) {
        return lookup(rawClass).feminine();
    }

    /** Classe de contexto (rua, prédio): entra na descrição da cena, mas nunca vira alerta. */
    public boolean isContext(String rawClass) {
        return lookup(rawClass).context();
    }

    /** Prioridade base da classe, sem considerar a proximidade. */
    public Priority basePriority(String rawClass) {
        return lookup(rawClass).basePriority();
    }

    /** Prioridade final = base, escalada em um nível se o objeto estiver muito próximo. */
    public Priority resolve(String rawClass, Proximity proximity) {
        Priority base = basePriority(rawClass);
        return proximity == Proximity.NEAR ? escalate(base) : base;
    }

    private Priority escalate(Priority priority) {
        return switch (priority) {
            case LOW -> Priority.MEDIUM;
            case MEDIUM -> Priority.HIGH;
            case HIGH -> Priority.HIGH;
        };
    }

    private ClassInfo lookup(String rawClass) {
        if (rawClass == null) {
            return DEFAULT_INFO;
        }
        return CATALOG.getOrDefault(rawClass.trim().toLowerCase(Locale.ROOT), DEFAULT_INFO);
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Objeto";
        }
        String trimmed = value.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1).toLowerCase(Locale.ROOT);
    }
}
