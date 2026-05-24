package br.edu.puc.fitjourneyai.core.flow;

import br.edu.puc.fitjourneyai.core.model.enums.ConversationFlowType;

public interface FlowHandler {

    ConversationFlowType getFlowType();
    FlowResult handle(FlowContext context);

}
