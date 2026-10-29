package com.shortener.orchestration.gates;

import com.shortener.orchestration.dag.GateBinding;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class GateRegistry {

    private final Map<String, GateEvaluator> evaluatorsByType;

    public GateRegistry(List<GateEvaluator> evaluators) {
        this.evaluatorsByType = evaluators.stream()
                .collect(Collectors.toMap(GateEvaluator::type, Function.identity()));
    }

    public GateResult evaluate(GateBinding binding, GateContext context) {
        GateEvaluator evaluator = evaluatorsByType.get(binding.type());
        if (evaluator == null) {
            return GateResult.fail("No gate evaluator registered for type '" + binding.type() + "'");
        }
        return evaluator.evaluate(binding, context);
    }
}
