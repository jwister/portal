package io.ztoken.portal.payment.trc20;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TronAddressCodecTest {

    @Test
    void convertsTronGridHexAccountAddressToBase58Check() {
        assertThat(TronAddressCodec.normalizeAccountAddress("0x74472e7cbcb09b4c3031f42bdf1564c9e5c4dccd"))
                .isEqualTo("TLa2f6TiSF7kqwwELhwbs5ZaPgACaoMsVZ");
    }

    @Test
    void preservesAlreadyNormalizedAddresses() {
        assertThat(TronAddressCodec.normalizeAccountAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE"))
                .isEqualTo("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE");
    }
}
