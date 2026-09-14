package errand.io;

import errand.economy.Shop;
import errand.engine.GameState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/** 도깨비 상점의 콘솔 진행. 거래 로직은 {@link Shop}에 있고 여기는 입출력만 한다. */
public final class ShopConsole {

    private final PrintStream out;
    private final BufferedReader in;
    private final boolean auto;

    public ShopConsole(PrintStream out, BufferedReader in, boolean auto) {
        this.out = out;
        this.in = in;
        this.auto = auto;
    }

    /** 플레이어가 떠날 때까지 상점을 돌린다. */
    public void run(Shop shop, GameState state) throws IOException {
        out.println();
        out.println("  도깨비가 봇짐을 풀어놓고 앉아 있다. 셈은 서툴러도 약속은 지키는 것들이다.");

        boolean autoSpent = false;

        while (true) {
            List<Shop.Offer> offers = shop.offers(state);

            out.println();
            out.println("  " + "─".repeat(64));
            out.println("  " + state);
            out.println("  " + "─".repeat(64));

            for (int i = 0; i < offers.size(); i++) {
                Shop.Offer o = offers.get(i);
                String price = o.soulCost() > 0 ? "혼력 %d".formatted(o.soulCost()) : "—";
                out.printf("  %d) %-18s %-10s %s%n", i + 1, o.label(), price, o.detail());
                if (!o.available()) out.printf("     └ %s%n", o.blockedReason());
            }
            out.printf("  %d) 떠난다%n", offers.size() + 1);
            out.print("> ");
            out.flush();

            int picked;
            if (auto) {
                // 자동 모드에서는 강화를 한 번만 시도해 경로를 밟고 떠난다.
                picked = (!autoSpent && offers.get(0).available()) ? 0 : offers.size();
                autoSpent = true;
                out.println((picked + 1) + "  (자동)");
            } else {
                String line = in.readLine();
                if (line == null) return;
                picked = parse(line, offers.size() + 1);
                if (picked < 0) {
                    out.println("  1에서 " + (offers.size() + 1) + " 사이로 입력하세요.");
                    continue;
                }
            }

            if (picked == offers.size()) {
                out.println();
                out.println("  도깨비가 봇짐을 여민다. \"또 오시오. 셈은 내가 손해 보는 쪽으로 하리다.\"");
                return;
            }

            Shop.Purchase result = shop.buy(state, offers.get(picked).item());
            out.println();
            out.println("  " + result.message());
        }
    }

    private static int parse(String line, int count) {
        try {
            int n = Integer.parseInt(line.trim());
            return (n >= 1 && n <= count) ? n - 1 : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
