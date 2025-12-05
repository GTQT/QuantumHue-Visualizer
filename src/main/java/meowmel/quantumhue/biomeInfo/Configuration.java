package meowmel.quantumhue.biomeInfo;

import meowmel.quantumhue.QuantumHueConfig;

public class Configuration {

    public static int getTextColor() {
        try {
            return Integer.parseInt(QuantumHueConfig.biome_info.TEXT_COLOR.replaceAll("#", ""), 16);
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }
}
