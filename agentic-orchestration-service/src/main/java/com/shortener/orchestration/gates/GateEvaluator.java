package com.shortener.orchestration.gates;

import com.shortener.orchestration.dag.GateBinding;

public interface GateEvaluator {

    /** Gate type key referenced from the DAG definition, e.g. "schema" or "approval". */
    String type();

    GateResult evaluate(GateBinding binding, GateContext context);
}
