package github.nighter.smartspawner.hooks.shops;

import github.nighter.smartspawner.utils.LanguageManager;
import org.bukkit.entity.Player;
import github.nighter.smartspawner.spawner.properties.SpawnerData;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public interface IShopIntegration {
    boolean sellAllItems(Player player, SpawnerData spawner);
    boolean isEnabled();

    default String formatPrice(double price, boolean useLanguageManager) {
        if (useLanguageManager && price >= 1000) {
            return getLanguageManager().formatNumber((long) price);
        }

        DecimalFormat df = new DecimalFormat("#,##0.00");
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        df.setDecimalFormatSymbols(symbols);
        return df.format(price);
    }

    LanguageManager getLanguageManager();
}

