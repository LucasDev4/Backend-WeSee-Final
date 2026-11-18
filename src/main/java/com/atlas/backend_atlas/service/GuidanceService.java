package com.atlas.backend_atlas.service;

import com.atlas.backend_atlas.dto.DetectedObject;
import com.atlas.backend_atlas.dto.GuidanceResponse;
import com.atlas.backend_atlas.dto.Position;
import com.atlas.backend_atlas.dto.Priority;
import com.atlas.backend_atlas.dto.Proximity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gera as frases em português que o app fala:
 * <ul>
 *   <li>{@link #generate}: o alerta principal (o objeto mais urgente), ex.: "Pessoa muito próxima à sua frente";</li>
 *   <li>{@link #describeScene}: tudo que a câmera vê, ex.: "Vejo duas pessoas à sua frente e um carro à sua direita."</li>
 * </ul>
 * Rua e prédio (classes de contexto) só aparecem na descrição da cena, nunca como alerta.
 */
@Service
@RequiredArgsConstructor
public class GuidanceService {

    private static final String NO_OBJECT_MESSAGE = "Nenhum objeto relevante detectado.";
    private static final String CLEAR_SCENE = "Caminho livre, nenhum objeto à vista.";
    private static final int MAX_SCENE_GROUPS = 5;

    private final PriorityRules priorityRules;

    public GuidanceResponse generate(List<DetectedObject> objects) {
        List<DetectedObject> relevant = objects == null ? List.of()
                : objects.stream().filter(o -> !priorityRules.isContext(o.className())).toList();
        if (relevant.isEmpty()) {
            return new GuidanceResponse(NO_OBJECT_MESSAGE, null, Priority.LOW);
        }
        DetectedObject mostUrgent = relevant.stream()
                .max(Comparator.comparing(DetectedObject::priority)
                        .thenComparing(DetectedObject::proximity)
                        .thenComparing(DetectedObject::confidence))
                .orElseThrow();
        return new GuidanceResponse(buildMessage(mostUrgent), mostUrgent.position(), mostUrgent.priority());
    }

    private String buildMessage(DetectedObject object) {
        String label = priorityRules.label(object.className());
        boolean feminine = priorityRules.isFeminine(object.className());
        StringBuilder message = new StringBuilder(label).append(" ");
        if (object.proximity() == Proximity.NEAR) {
            message.append(feminine ? "muito próxima " : "muito próximo ");
        }
        message.append(directionPhrase(object.position()));
        return message.toString();
    }

    /** Frase única com tudo que foi detectado, agrupando iguais (mesma classe e mesma direção) e contando. */
    public String describeScene(List<DetectedObject> objects) {
        if (objects == null || objects.isEmpty()) {
            return CLEAR_SCENE;
        }
        record Group(String className, Position position, int count, Priority priority, Proximity proximity) {}
        Map<String, Group> groups = new LinkedHashMap<>();
        for (DetectedObject o : objects) {
            String key = o.className().trim().toLowerCase(Locale.ROOT) + "|" + o.position();
            Group g = groups.get(key);
            groups.put(key, g == null
                    ? new Group(o.className(), o.position(), 1, o.priority(), o.proximity())
                    : new Group(g.className(), g.position(), g.count() + 1,
                            g.priority().compareTo(o.priority()) >= 0 ? g.priority() : o.priority(),
                            g.proximity().compareTo(o.proximity()) >= 0 ? g.proximity() : o.proximity()));
        }
        List<String> phrases = new ArrayList<>();
        groups.values().stream()
                .sorted(Comparator.comparing(Group::priority).thenComparing(Group::proximity).thenComparing(Group::count).reversed())
                .limit(MAX_SCENE_GROUPS)
                .forEach(g -> phrases.add(groupPhrase(g.className(), g.position(), g.count(), g.proximity())));
        return "Vejo " + join(phrases) + ".";
    }

    private String groupPhrase(String className, Position position, int count, Proximity proximity) {
        boolean feminine = priorityRules.isFeminine(className);
        boolean plural = count > 1;
        String quantity = count == 1 ? (feminine ? "uma" : "um")
                : count == 2 ? (feminine ? "duas" : "dois")
                : (feminine ? "várias" : "vários");
        String noun = (plural ? priorityRules.plural(className) : priorityRules.label(className)).toLowerCase(Locale.ROOT);
        StringBuilder phrase = new StringBuilder(quantity).append(" ").append(noun);
        if (proximity == Proximity.NEAR && !priorityRules.isContext(className)) {
            phrase.append(" muito próxim").append(feminine ? "a" : "o").append(plural ? "s" : "");
        }
        return phrase.append(" ").append(directionPhrase(position)).toString();
    }

    private String join(List<String> items) {
        if (items.size() == 1) return items.get(0);
        return String.join(", ", items.subList(0, items.size() - 1)) + " e " + items.get(items.size() - 1);
    }

    private String directionPhrase(Position position) {
        if (position == null) {
            return "por perto";
        }
        return switch (position) {
            case LEFT -> "à sua esquerda";
            case CENTER -> "à sua frente";
            case RIGHT -> "à sua direita";
        };
    }
}
