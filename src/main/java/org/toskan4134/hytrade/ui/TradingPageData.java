package org.toskan4134.hytrade.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Data class for UI events in the trading page.
 * Contains the action that was triggered by the user.
 */
public class TradingPageData {
    public static final BuilderCodec<TradingPageData> CODEC;

    static {
        BuilderCodec<TradingPageData> codec;
        try {
            codec = BuilderCodec.builder(TradingPageData.class, TradingPageData::new)
                    .addField(
                            new KeyedCodec<>("Action", Codec.STRING),
                            (data, value) -> data.action = value,
                            data -> data.action
                    )
                    .build();
        } catch (Exception e) {
            codec = BuilderCodec.builder(TradingPageData.class, TradingPageData::new).build();
            e.printStackTrace();
        }
        CODEC = codec;
    }

    public String action = "";

    public TradingPageData() {
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
