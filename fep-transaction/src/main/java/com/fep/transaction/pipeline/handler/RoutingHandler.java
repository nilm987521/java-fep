package com.fep.transaction.pipeline.handler;

import com.fep.transaction.exception.TransactionException;
import com.fep.transaction.pipeline.PipelineContext;
import com.fep.transaction.pipeline.PipelineHandler;
import com.fep.transaction.pipeline.PipelineStage;
import com.fep.transaction.routing.RoutingResult;
import com.fep.transaction.routing.TransactionRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pipeline handler for transaction routing.
 */
public class RoutingHandler implements PipelineHandler {

    private static final Logger log = LoggerFactory.getLogger(RoutingHandler.class);

    private final TransactionRouter router;

    public RoutingHandler(TransactionRouter router) {
        this.router = router;
    }

    @Override
    public PipelineStage getStage() {
        return PipelineStage.ROUTING;
    }

    @Override
    public void handle(PipelineContext context) {
        log.debug("[{}] Routing transaction", context.getPipelineId());

        RoutingResult result = router.route(context.getRequest());

        if (!result.isRouted()) {
            throw TransactionException.routingFailed(result.getMessage());
        }

        context.setRoutingResult(result);

        log.info("[{}] Routed to {} (timeout: {}ms)",
                context.getPipelineId(),
                result.getDestination(),
                result.getTimeoutMs());
    }
}
