package io.ztoken.portal.payment.trc20;

import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.payment.domain.PaymentAddress;
import io.ztoken.portal.payment.repository.PaymentAddressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 将 application.yml 声明的地址幂等写入地址池，绝不覆盖已有地址运行状态。 */
@Component
public class Trc20AddressPoolInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Trc20AddressPoolInitializer.class);
    private final PaymentProperties properties;
    private final PaymentAddressRepository addresses;

    public Trc20AddressPoolInitializer(PaymentProperties properties, PaymentAddressRepository addresses) {
        this.properties = properties;
        this.addresses = addresses;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (String address : properties.getTrc20().getAddresses()) {
            if (address == null || address.isBlank() || addresses.findByAddress(address).isPresent()) continue;
            addresses.save(new PaymentAddress(address.trim(), Instant.now()));
            log.info("已导入 TRC20 收款地址：地址={}", address);
        }
    }
}
