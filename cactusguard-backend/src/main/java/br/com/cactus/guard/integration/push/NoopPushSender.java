package br.com.cactus.guard.integration.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Padrao: envio real desligado. Apenas registra (debug) que enviaria, sem conteudo. */
@Slf4j
@Component
@ConditionalOnProperty(name = "cactus.push.provider", havingValue = "noop", matchIfMissing = true)
public class NoopPushSender implements PushSenderPort {

    @Override
    public PushResult send(String deviceToken, PushMessage message) {
        log.debug("Push desligado (noop): enviaria type={} id={} para token={}",
                message.type(), message.id(), PushSenderPort.mask(deviceToken));
        return PushResult.FAILED;
    }
}
