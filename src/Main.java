import javazoom.jl.player.Player;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * FW0 - Microsoft Windows風 偽警告画面シミュレーション（演出用）
 *
 * 仕様:
 * - バックグラウンド: 情報量の多い Microsoft サポート / Defender 技術診断ポータル画面
 * - 中央奥: Windows Defender セキュリティ センター風ウィンドウ
 * - 中央最前面 & 右下: 偽警告特有の「ふわふわ拡大縮小（ズームイン・アウト）」する警告ウィンドウ
 *   （画像を事前レンダリングしてハードウェア補間拡大縮小することでカクつきゼロを実現）
 * - 左側: Windows XP風のコマンドプロンプト画面（常時高速スクロール）
 * - 自律的に動き回る偽マウスカーソルが1つ存在
 * - ピポピポ警報音 ＋ voice.mp3 の音声再生
 * - 基本10秒で自動終了、画面のどこかをクリックすると残り時間+5秒追加
 * - ESCキーで即座に終了
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FakeWarningFrame frame = new FakeWarningFrame();
            frame.setVisible(true);
        });
    }

    /**
     * 偽警告サウンド（ピポピポ音）＆ voice.mp3 再生エンジン
     */
    static class SoundEngine {
        private volatile boolean running = true;
        private volatile Player mp3Player;
        private Thread voiceThread;
        private Thread pipoThread;

        public void start() {
            playCriticalErrorChordAsync();
            startPipoPipoLoop();
            startVoiceMp3Loop();
        }

        public void stop() {
            running = false;
            if (mp3Player != null) {
                mp3Player.close();
            }
            if (voiceThread != null) {
                voiceThread.interrupt();
            }
            if (pipoThread != null) {
                pipoThread.interrupt();
            }
        }

        private void startVoiceMp3Loop() {
            if (!isVoiceAvailable()) {
                return;
            }
            voiceThread = new Thread(() -> {
                while (running && !Thread.currentThread().isInterrupted()) {
                    try (InputStream is = openVoiceStream()) {
                        if (is == null) break;
                        mp3Player = new Player(new BufferedInputStream(is));
                        mp3Player.play();
                    } catch (Exception e) {
                        break;
                    }
                    try {
                        Thread.sleep(350);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "VoiceMp3Thread");
            voiceThread.setDaemon(true);
            voiceThread.start();
        }

        public static boolean isVoiceAvailable() {
            if (Main.class.getResource("/voice.mp3") != null) {
                return true;
            }
            return findExternalVoiceFile() != null;
        }

        private static InputStream openVoiceStream() throws Exception {
            File ext = findExternalVoiceFile();
            if (ext != null) {
                return new FileInputStream(ext);
            }
            return Main.class.getResourceAsStream("/voice.mp3");
        }

        public static File findExternalVoiceFile() {
            String[] candidatePaths = {"voice.mp3", "src/voice.mp3", "resources/voice.mp3", "../voice.mp3"};
            for (String path : candidatePaths) {
                File f = new File(path);
                if (f.exists() && f.isFile()) {
                    return f;
                }
            }
            return null;
        }

        private void startPipoPipoLoop() {
            pipoThread = new Thread(() -> {
                float sampleRate = 44100f;
                AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
                try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                    line.open(format);
                    line.start();

                    double piFreq = 988.0; // B5
                    double poFreq = 784.0; // G5

                    while (running && !Thread.currentThread().isInterrupted()) {
                        writePipoTone(line, sampleRate, piFreq, 135, 0.28);
                        if (!running) break;
                        writePipoTone(line, sampleRate, poFreq, 135, 0.28);
                        if (!running) break;
                        writePipoTone(line, sampleRate, piFreq, 135, 0.28);
                        if (!running) break;
                        writePipoTone(line, sampleRate, poFreq, 155, 0.28);
                        if (!running) break;

                        writeSilence(line, sampleRate, 260);
                    }
                    line.drain();
                } catch (Exception ignored) {
                }
            }, "PipoPipoAlarmThread");
            pipoThread.setDaemon(true);
            pipoThread.start();
        }

        public void playFastPipoBurstAsync() {
            if (!running) return;
            Thread t = new Thread(() -> {
                try {
                    float sampleRate = 44100f;
                    AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
                    try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                        line.open(format);
                        line.start();
                        writePipoTone(line, sampleRate, 1318.5, 80, 0.35);
                        writePipoTone(line, sampleRate, 1046.5, 80, 0.35);
                        if (running) {
                            writePipoTone(line, sampleRate, 1318.5, 80, 0.35);
                            writePipoTone(line, sampleRate, 1046.5, 100, 0.35);
                        }
                        line.drain();
                    }
                } catch (Exception ignored) {
                }
            }, "FastPipoBurstThread");
            t.setDaemon(true);
            t.start();
        }

        public void playCriticalErrorChordAsync() {
            if (!running) return;
            Thread t = new Thread(() -> {
                try {
                    float sampleRate = 44100f;
                    AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
                    try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                        line.open(format);
                        line.start();

                        int durationMs = 300;
                        int numSamples = (int) (sampleRate * durationMs / 1000);
                        byte[] buf = new byte[numSamples * 2];

                        for (int i = 0; i < numSamples; i++) {
                            double time = i / sampleRate;
                            double env = Math.exp(-time * 8.0);
                            double wave = 0.45 * Math.sin(2 * Math.PI * 220.0 * time)
                                    + 0.35 * Math.sin(2 * Math.PI * 311.13 * time)
                                    + 0.25 * Math.sin(2 * Math.PI * 440.0 * time)
                                    + 0.15 * Math.signum(Math.sin(2 * Math.PI * 110.0 * time));

                            short val = (short) (wave * env * 16000);
                            buf[i * 2] = (byte) (val & 0xFF);
                            buf[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
                        }
                        line.write(buf, 0, buf.length);
                        line.drain();
                    }
                } catch (Exception ignored) {
                }
            }, "CriticalChordThread");
            t.setDaemon(true);
            t.start();
        }

        private void writePipoTone(SourceDataLine line, float sampleRate, double freq, int ms, double volume) {
            int samples = (int) (sampleRate * ms / 1000);
            byte[] buf = new byte[samples * 2];
            int attackSamples = Math.max(1, (int) (sampleRate * 0.008));
            int releaseSamples = Math.max(1, (int) (sampleRate * 0.015));

            for (int i = 0; i < samples; i++) {
                double t = i / sampleRate;
                double fundamental = Math.sin(2 * Math.PI * freq * t);
                double harmonic2 = 0.25 * Math.sin(2 * Math.PI * (freq * 2.0) * t);
                double harmonic3 = 0.15 * Math.sin(2 * Math.PI * (freq * 3.0) * t);
                double wave = (fundamental + harmonic2 + harmonic3) / 1.4;

                double env = 1.0;
                if (i < attackSamples) {
                    env = (double) i / attackSamples;
                } else if (i > samples - releaseSamples) {
                    env = (double) (samples - i) / releaseSamples;
                }

                short sample = (short) (wave * env * volume * Short.MAX_VALUE);
                buf[i * 2] = (byte) (sample & 0xFF);
                buf[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
            }
            line.write(buf, 0, buf.length);
        }

        private void writeSilence(SourceDataLine line, float sampleRate, int ms) {
            int samples = (int) (sampleRate * ms / 1000);
            byte[] buf = new byte[samples * 2];
            line.write(buf, 0, buf.length);
        }
    }

    static class FakeWarningFrame extends JFrame {
        private final SimulationPanel canvas;

        public FakeWarningFrame() {
            setTitle("Windows Security Alert Simulation (Press ESC to Exit)");
            setUndecorated(true);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            setSize(screenSize);
            setLocationRelativeTo(null);
            setAlwaysOnTop(true);

            canvas = new SimulationPanel(screenSize.width, screenSize.height, this::closeSimulation);
            setContentPane(canvas);

            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
                if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    closeSimulation();
                    return true;
                }
                return false;
            });

            canvas.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "exitSim");
            canvas.getActionMap().put("exitSim", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    closeSimulation();
                }
            });
        }

        private void closeSimulation() {
            canvas.stopSimulation();
            dispose();
            System.exit(0);
        }
    }

    static class SimulationPanel extends JPanel {
        private static final int TICK_MS = 20; // 50 FPS

        private static final Font FONT_UI_11 = new Font("Yu Gothic UI", Font.PLAIN, 11);
        private static final Font FONT_UI_11_B = new Font("Yu Gothic UI", Font.BOLD, 11);
        private static final Font FONT_UI_12 = new Font("Yu Gothic UI", Font.PLAIN, 12);
        private static final Font FONT_UI_12_B = new Font("Yu Gothic UI", Font.BOLD, 12);
        private static final Font FONT_UI_13 = new Font("Yu Gothic UI", Font.PLAIN, 13);
        private static final Font FONT_UI_13_B = new Font("Yu Gothic UI", Font.BOLD, 13);
        private static final Font FONT_UI_14 = new Font("Yu Gothic UI", Font.PLAIN, 14);
        private static final Font FONT_UI_14_B = new Font("Yu Gothic UI", Font.BOLD, 14);
        private static final Font FONT_UI_15_B = new Font("Yu Gothic UI", Font.BOLD, 15);
        private static final Font FONT_UI_16_B = new Font("Yu Gothic UI", Font.BOLD, 16);
        private static final Font FONT_UI_18_B = new Font("Yu Gothic UI", Font.BOLD, 18);
        private static final Font FONT_UI_20_B = new Font("Yu Gothic UI", Font.BOLD, 20);
        private static final Font FONT_UI_24_B = new Font("Yu Gothic UI", Font.BOLD, 24);
        private static final Font FONT_SEGOE_18_B = new Font("Segoe UI", Font.BOLD, 18);
        private static final Font FONT_TAHOMA_13_B = new Font("Tahoma", Font.BOLD, 13);
        private static final Font FONT_CONSOLAS_12 = new Font("Consolas", Font.PLAIN, 12);
        private static final Font FONT_CONSOLAS_13 = new Font("Consolas", Font.PLAIN, 13);

        private final int screenW;
        private final int screenH;
        private final Runnable onExit;
        private final Random random = new Random();
        private final SoundEngine soundEngine = new SoundEngine();

        // 事前レンダリングしたキャッシュ画像（毎フレームの描画・拡大縮小を高速化）
        private final BufferedImage backgroundCache;
        private final BufferedImage centerModalCache;
        private final BufferedImage floatingCardCache;
        private static final int MODAL_W = 550;
        private static final int MODAL_H = 310;
        private static final int FLOAT_CARD_W = 420;
        private static final int FLOAT_CARD_H = 185;

        // 残り時間（ミリ秒）: 初期10秒
        private long remainingMs = 10_000;
        private long lastUpdateTimeNs;
        private int tickCount = 0;
        private int shakeTicks = 0;

        // Windows XP風コマンドプロンプトの状態
        private int cmdX, cmdY, cmdW, cmdH;
        private final List<String> cmdLines = new ArrayList<>();
        private int cmdLogCounter = 0;

        // 偽マウスカーソル (1つ)
        private double fakeCursorX;
        private double fakeCursorY;
        private double targetCursorX;
        private double targetCursorY;
        private int fakeCursorClickAnim = 0;

        private final Timer mainTimer;

        private static final String[] CMD_LOG_TEMPLATES = {
                "C:\\WINDOWS\\system32\\drivers\\tcpip.sys ... [Threat: Trojan.Win32.Generic]",
                "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run ... [ACCESS BLOCKED]",
                "WARNING: Outbound connection blocked -> 198.51.100.%d:4443 (TCP)",
                "Memory dump 0x0040FA10: 4D 5A 90 00 03 00 00 00 04 00 00 00 FF FF 00 00",
                "Verifying system integrity ... CRC32 mismatch in kernel32.dll",
                "Isolating infected file: Win32/Spyware.Stealer.FW0 ... QUARANTINE PENDING",
                "C:\\WINDOWS\\system32\\svchost.exe [PID: %d] -> Suspicious thread 0x%04X",
                "Defender Engine: Scanning sector 0x00%04X ... [CRITICAL ALERT]",
                "Firewall protection: Locking network adapter credentials...",
                "System restore point verification: Volume{7c91a4f0} -> LOCKED"
        };

        public SimulationPanel(int screenW, int screenH, Runnable onExit) {
            this.screenW = screenW;
            this.screenH = screenH;
            this.onExit = onExit;
            setFocusable(true);
            setDoubleBuffered(true);

            // 重くならないよう、高密度な背景と「ふわふわ拡大縮小するウィンドウ」を画像に事前描画
            this.backgroundCache = createDenseBackgroundImage(screenW, screenH);
            this.centerModalCache = createCenterModalImage(MODAL_W, MODAL_H);
            this.floatingCardCache = createFloatingCardImage(FLOAT_CARD_W, FLOAT_CARD_H);

            initCmdWindow();
            initFakeCursor();

            soundEngine.start();

            // 画面のどこかをクリックすると残り時間+5秒追加 + 揺れ＆ピポピポ音
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    remainingMs += 5_000;
                    shakeTicks = 12;
                    soundEngine.playFastPipoBurstAsync();
                }
            });

            lastUpdateTimeNs = System.nanoTime();
            mainTimer = new Timer(TICK_MS, e -> updateSimulation());
            mainTimer.start();
        }

        public void stopSimulation() {
            if (mainTimer != null && mainTimer.isRunning()) {
                mainTimer.stop();
            }
            soundEngine.stop();
        }

        private void initCmdWindow() {
            cmdW = 570;
            cmdH = 330;
            cmdX = 36;
            cmdY = 125;

            cmdLines.add("Microsoft Windows XP [Version 5.1.2600]");
            cmdLines.add("(C) Copyright 1985-2001 Microsoft Corp.");
            cmdLines.add("");
            cmdLines.add("C:\\WINDOWS\\system32>dir /s /b C:\\WINDOWS\\system32\\*.*");
            for (int i = 0; i < 14; i++) {
                appendNextCmdLog();
            }
        }

        private void appendNextCmdLog() {
            cmdLogCounter++;
            String template = CMD_LOG_TEMPLATES[cmdLogCounter % CMD_LOG_TEMPLATES.length];
            String formatted = String.format(template,
                    10 + random.nextInt(240),
                    1000 + random.nextInt(8000),
                    random.nextInt(0xFFFF));
            cmdLines.add(String.format("[%05d] %s", cmdLogCounter, formatted));
            while (cmdLines.size() > 16) {
                cmdLines.remove(0);
            }
        }

        private void initFakeCursor() {
            fakeCursorX = screenW * 0.55;
            fakeCursorY = screenH * 0.55;
            pickNextCursorTarget();
        }

        private void pickNextCursorTarget() {
            int centerX = screenW / 2;
            int centerY = screenH / 2;
            if (random.nextBoolean()) {
                targetCursorX = centerX + 60 + random.nextInt(180);
                targetCursorY = centerY + 90 + random.nextInt(90);
            } else {
                targetCursorX = centerX - 260 + random.nextInt(520);
                targetCursorY = centerY - 180 + random.nextInt(360);
            }
        }

        private void updateSimulation() {
            long nowNs = System.nanoTime();
            long elapsedMs = (nowNs - lastUpdateTimeNs) / 1_000_000;
            lastUpdateTimeNs = nowNs;

            remainingMs -= elapsedMs;
            if (remainingMs <= 0) {
                remainingMs = 0;
                onExit.run();
                return;
            }

            tickCount++;
            if (shakeTicks > 0) {
                shakeTicks--;
            }

            if (tickCount % 2 == 0) {
                appendNextCmdLog();
            }

            double dx = targetCursorX - fakeCursorX;
            double dy = targetCursorY - fakeCursorY;
            double dist = Math.hypot(dx, dy);
            if (dist < 14) {
                fakeCursorClickAnim = 10;
                pickNextCursorTarget();
            } else {
                double speed = Math.min(14.0, Math.max(5.0, dist * 0.12));
                fakeCursorX += (dx / dist) * speed + (random.nextDouble() - 0.5) * 2.5;
                fakeCursorY += (dy / dist) * speed + (random.nextDouble() - 0.5) * 2.5;
            }
            if (fakeCursorClickAnim > 0) {
                fakeCursorClickAnim--;
            }

            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int shakeX = (shakeTicks > 0) ? (random.nextInt(9) - 4) : 0;
            int shakeY = (shakeTicks > 0) ? (random.nextInt(9) - 4) : 0;

            // 1. バックグラウンド（高密度なMicrosoftセキュリティ診断ページ：事前キャッシュ画像で一瞬で描画）
            g2.drawImage(backgroundCache, 0, 0, null);

            // 2. 中央奥: Windows Defender セキュリティ センター大ウィンドウ
            drawDefenderSecurityCenterWindow(g2, shakeX, shakeY);

            // 3. 左側: Windows XP風コマンドプロンプト画面（常時高速スクロール）
            drawWindowsXpCommandPrompt(g2);

            // 4. 中央手前: ふわふわ拡大縮小（ズームイン・アウト）する「Windows Defender セキュリティ警告」ウィンドウ
            drawZoomingCenterModal(g2, shakeX, shakeY);

            // 5. 右下: 別の周期でふわふわ拡大縮小する「緊急隔離モニター」ウィンドウ
            drawZoomingFloatingCard(g2, shakeX, shakeY);

            // 6. 偽マウスカーソル（1つ、最前面）
            drawFakeMouseCursor(g2, (int) Math.round(fakeCursorX), (int) Math.round(fakeCursorY));
        }

        /**
         * 1. 情報量を大幅に増やしたMicrosoft公式サポート＆セキュリティ診断ポータル背景画像を生成
         */
        private BufferedImage createDenseBackgroundImage(int w, int h) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 全体背景
            g2.setColor(new Color(234, 239, 245));
            g2.fillRect(0, 0, w, h);

            // --- (A) ブラウザタブバー & アドレスバー ---
            g2.setColor(new Color(32, 39, 50));
            g2.fillRect(0, 0, w, 30);
            // アクティブタブ
            g2.setColor(new Color(224, 229, 236));
            g2.fillRoundRect(12, 4, 340, 28, 8, 8);
            g2.setColor(new Color(20, 20, 20));
            g2.setFont(FONT_UI_11_B);
            g2.drawString("🛡 Microsoft Defender セキュリティ センター - 緊急診断  ×", 24, 21);
            // 非アクティブタブ
            g2.setColor(new Color(50, 60, 75));
            g2.fillRoundRect(358, 6, 280, 24, 8, 8);
            g2.setColor(new Color(210, 220, 230));
            g2.setFont(FONT_UI_11);
            g2.drawString("⚠ システム整合性レポート (KB5049812)   ×", 370, 21);
            g2.fillRoundRect(644, 6, 250, 24, 8, 8);
            g2.drawString("🔒 Windows ファイアウォール ネットワーク遮断ログ   ×", 656, 21);

            // URLアドレスバー
            int addrY = 30;
            g2.setColor(new Color(224, 229, 236));
            g2.fillRect(0, addrY, w, 34);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(110, addrY + 4, w - 280, 25, 12, 12);
            g2.setColor(new Color(16, 124, 65));
            g2.setFont(FONT_UI_12_B);
            g2.drawString("🔒 https://support.microsoft.com/ja-jp/windows/security-defender-diagnostic-center?error=DW6VB36&status=critical_lock", 126, addrY + 21);

            // --- (B) ブラウザ直下の赤いSmartScreen警告ストリップ ---
            int alertBarY = addrY + 34;
            g2.setColor(new Color(185, 28, 28));
            g2.fillRect(0, alertBarY, w, 26);
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_UI_12_B);
            g2.drawString("⚠ Microsoft Defender SmartScreen 警告: カーネルメモリ領域への不正アクセスと資格情報窃取の試行を検知したため、セッションを保護ロックしています。", 24, alertBarY + 17);

            // --- (C) Microsoft ヘッダーナビゲーション ---
            int navY = alertBarY + 26;
            g2.setColor(Color.WHITE);
            g2.fillRect(0, navY, w, 44);
            g2.setColor(new Color(218, 222, 228));
            g2.drawLine(0, navY + 43, w, navY + 43);

            int logoX = 28;
            int logoY = navY + 11;
            g2.setColor(new Color(242, 80, 34));
            g2.fillRect(logoX, logoY, 10, 10);
            g2.setColor(new Color(127, 186, 0));
            g2.fillRect(logoX + 12, logoY, 10, 10);
            g2.setColor(new Color(0, 164, 239));
            g2.fillRect(logoX, logoY + 12, 10, 10);
            g2.setColor(new Color(255, 185, 0));
            g2.fillRect(logoX + 12, logoY + 12, 10, 10);

            g2.setColor(new Color(35, 35, 35));
            g2.setFont(FONT_SEGOE_18_B);
            g2.drawString("Microsoft", logoX + 30, logoY + 17);
            g2.setFont(FONT_UI_13_B);
            g2.drawString("|  Windows セキュリティ サポート  |  脅威インテリジェンス  |  KB5049812 緊急アドバイザリ  |  システム検疫ポータル", logoX + 125, logoY + 16);

            g2.setFont(FONT_UI_11_B);
            g2.setColor(new Color(196, 43, 28));
            g2.drawString("● セキュリティ状態: 危険 (脅威 27 件 / 通信遮断中)", w - 310, logoY + 16);

            // --- (D) ヒーローバナー（技術メタデータ大量表示） ---
            int bannerY = navY + 44;
            int bannerH = 128;
            GradientPaint heroGrad = new GradientPaint(0, bannerY, new Color(0, 78, 152), w, bannerY + bannerH, new Color(12, 38, 78));
            g2.setPaint(heroGrad);
            g2.fillRect(0, bannerY, w, bannerH);

            g2.setColor(Color.WHITE);
            g2.setFont(FONT_UI_24_B);
            g2.drawString("Windows Defender セキュリティ 診断および脅威封じ込めポータル", 28, bannerY + 36);

            g2.setFont(FONT_UI_12);
            g2.setColor(new Color(215, 235, 255));
            g2.drawString("インシデントID: MS-DEF-2026-DW6VB36   |   対象OS: Windows NT 10.0 (x64)   |   定義ファイル: v1.419.620.0   |   エラーコード: 0x80070422 / 0xC0000005", 28, bannerY + 62);
            g2.drawString("検出シグネチャ: Trojan:Win32/Banker.Injector!mt, Spyware:Win32/KeyLogger.Sim, Ransom:Win32/CryptLock.FakeSim, Backdoor:Win32/RemoteShell.FW0", 28, bannerY + 84);

            // バナー内の4つのメトリクスバッジ
            String[] badges = {
                    "⚠ 重大な脅威: 27 件検出",
                    "🔒 ファイアウォール: 緊急遮断モード",
                    "🛡 レジストリ保護: 書き込み凍結中",
                    "🌐 外部不正通信: 142 パケット遮断"
            };
            for (int i = 0; i < badges.length; i++) {
                int bx = 28 + i * 285;
                int by = bannerY + 94;
                g2.setColor(i == 0 ? new Color(209, 52, 56) : new Color(20, 62, 118));
                g2.fillRoundRect(bx, by, 270, 26, 6, 6);
                g2.setColor(new Color(120, 175, 240));
                g2.drawRoundRect(bx, by, 270, 26, 6, 6);
                g2.setColor(Color.WHITE);
                g2.setFont(FONT_UI_12_B);
                g2.drawString(badges[i], bx + 12, by + 17);
            }

            // --- (E) 背景3カラムの高密度情報パネル（左右の余白までびっしり情報を配置） ---
            int gridY = bannerY + bannerH + 12;
            int gridH = h - gridY - 34;
            int col1W = (int) (w * 0.31);
            int col2W = (int) (w * 0.36);
            int col3W = w - col1W - col2W - 48;

            int col1X = 12;
            int col2X = col1X + col1W + 12;
            int col3X = col2X + col2W + 12;

            // 【左カラム】カーネル＆システムファイル検査テレメトリ
            drawCardFrame(g2, col1X, gridY, col1W, gridH, "■ システムファイル整合性・メモリ保護テレメトリ (SFC /SCANNOW)");
            String[][] sysFiles = {
                    {"ntoskrnl.exe", "0xFFFFF8000410", "CRC不一致 [要修復]"},
                    {"drivers\\tcpip.sys", "0xFFFFF880019A", "不正フック検出 [隔離]"},
                    {"system32\\lsass.exe", "0x00007FF6A104", "資格情報アクセス遮断"},
                    {"system32\\svchost.exe", "0x00007FF6B210", "不審スレッド停止済"},
                    {"system32\\winlogon.exe", "0x00007FF6C008", "トークン改ざん防止"},
                    {"system32\\crypt32.dll", "0x00007FF8114C", "証明書ストア保護ロック"},
                    {"system32\\ws2_32.dll", "0x00007FF82390", "ソケット通信監視中"},
                    {"system32\\advapi32.dll", "0x00007FF84412", "レジストリ権限ロック"},
                    {"system32\\kernel32.dll", "0x00007FF855A0", "ヒープ保護検証中"},
                    {"system32\\user32.dll", "0x00007FF866B4", "入力フック監視中"},
                    {"Explorer.exe", "0x00007FF70018", "シェル拡張ブロック"}
            };
            int rowY = gridY + 46;
            g2.setFont(FONT_CONSOLAS_12);
            for (String[] row : sysFiles) {
                if (rowY + 18 > gridY + gridH - 120) break;
                g2.setColor(new Color(243, 246, 250));
                g2.fillRect(col1X + 10, rowY - 12, col1W - 20, 18);
                g2.setColor(new Color(25, 25, 25));
                g2.drawString(row[0], col1X + 14, rowY);
                g2.setColor(new Color(90, 100, 115));
                g2.drawString(row[1], col1X + 165, rowY);
                g2.setColor(new Color(196, 43, 28));
                g2.setFont(FONT_UI_11_B);
                g2.drawString(row[2], col1X + col1W - 135, rowY);
                g2.setFont(FONT_CONSOLAS_12);
                rowY += 22;
            }

            // 左カラム下部：リソース負荷メーター
            int meterY = gridY + gridH - 105;
            g2.setFont(FONT_UI_12_B);
            g2.setColor(new Color(30, 30, 30));
            g2.drawString("リアルタイム保護 リソース監視:", col1X + 14, meterY);
            drawMiniBar(g2, col1X + 14, meterY + 10, col1W - 28, "CPU 暗号化検査負荷 (94%)", 0.94, new Color(209, 52, 56));
            drawMiniBar(g2, col1X + 14, meterY + 40, col1W - 28, "物理メモリ隔離領域 (82% / 13.1 GB)", 0.82, new Color(216, 110, 0));
            drawMiniBar(g2, col1X + 14, meterY + 70, col1W - 28, "I/O ディスク検疫スキャン (89%)", 0.89, new Color(0, 103, 184));

            // 【中央カラム】技術診断レポート＆KB詳細
            drawCardFrame(g2, col2X, gridY, col2W, gridH, "■ セキュリティ勧告 KB5049812 - 重大な脅威の分析レポート");
            String[] kbLines = {
                    "【現象】システムメモリおよびスタートアップ構成に対する不正な変更試行が",
                    "確認されました。攻撃コードによる二次被害（保存されたパスワード、ブラウザ",
                    "Cookie、暗号化キーの外部送信）を防止するため、Windows Defender が",
                    "緊急アイソレーション（隔離）プロトコルを実行しています。",
                    "",
                    "【検出された不審なレジストリ・サービス項目】",
                    " • HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run\\WinSecUpdate",
                    " • HKLM\\SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces",
                    " • HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                    " • C:\\Users\\Public\\AppData\\Local\\Temp\\svchost_inject_sim.dll",
                    "",
                    "【現在実行中の自動保護プロセス】",
                    " 1. 不審な外部IPへのTCP/UDPポート通信の強制遮断 (Port 4444, 8443, 3389)",
                    " 2. 資格情報マネージャー (Vault) および SAM データベースの読み取りロック",
                    " 3. ボリューム シャドウ コピー (VSS) の削除防止ロック",
                    " 4. カーネルモードドライバ署名検証およびメモリダンプ解析"
            };
            int kbY = gridY + 48;
            g2.setFont(FONT_UI_12);
            for (String line : kbLines) {
                if (kbY > gridY + gridH - 20) break;
                if (line.startsWith("【")) {
                    g2.setColor(new Color(0, 90, 158));
                    g2.setFont(FONT_UI_12_B);
                } else if (line.startsWith(" •")) {
                    g2.setColor(new Color(180, 35, 30));
                    g2.setFont(FONT_CONSOLAS_12);
                } else {
                    g2.setColor(new Color(45, 45, 45));
                    g2.setFont(FONT_UI_12);
                }
                g2.drawString(line, col2X + 14, kbY);
                kbY += 22;
            }

            // 【右カラム】ネットワーク遮断ログ＆セキュリティステータス
            drawCardFrame(g2, col3X, gridY, col3W, gridH, "■ ファイアウォール 外部通信遮断ログ & 保護状態");
            String[][] netLogs = {
                    {"BLOCKED", "198.51.100.44:4444", "Trojan C2 Beaconing"},
                    {"BLOCKED", "203.0.113.192:8443", "TLS Credential Upload"},
                    {"BLOCKED", "192.0.2.88:3389", "RDP BruteForce Attack"},
                    {"BLOCKED", "198.51.100.109:445", "SMB Lateral Movement"},
                    {"BLOCKED", "203.0.113.77:9001", "Unauthorized Proxy"},
                    {"BLOCKED", "192.0.2.215:21", "FTP Data Exfiltration"},
                    {"LOCKED", "Localhost:135/445", "RPC/NetBIOS Quarantine"}
            };
            int netY = gridY + 46;
            for (String[] nl : netLogs) {
                if (netY + 20 > gridY + gridH - 150) break;
                g2.setColor(new Color(253, 236, 236));
                g2.fillRect(col3X + 10, netY - 13, col3W - 20, 20);
                g2.setColor(new Color(196, 43, 28));
                g2.setFont(FONT_UI_11_B);
                g2.drawString("[" + nl[0] + "]", col3X + 14, netY);
                g2.setColor(new Color(30, 30, 30));
                g2.setFont(FONT_CONSOLAS_12);
                g2.drawString(nl[1], col3X + 86, netY);
                g2.setColor(new Color(80, 80, 80));
                g2.setFont(FONT_UI_11);
                g2.drawString(nl[2], col3X + 225, netY);
                netY += 24;
            }

            // 右カラム下部の緊急警告ボックス
            int warnBoxY = gridY + gridH - 140;
            g2.setColor(new Color(255, 244, 229));
            g2.fillRoundRect(col3X + 12, warnBoxY, col3W - 24, 126, 8, 8);
            g2.setColor(new Color(216, 110, 0));
            g2.drawRoundRect(col3X + 12, warnBoxY, col3W - 24, 126, 8, 8);

            g2.setColor(new Color(180, 60, 0));
            g2.setFont(FONT_UI_13_B);
            g2.drawString("⚠ セキュリティ保護ロックに関する重要なお知らせ", col3X + 24, warnBoxY + 24);
            g2.setColor(new Color(50, 50, 50));
            g2.setFont(FONT_UI_12);
            g2.drawString("・スキャン完了前に強制終了すると整合性が失われる恐れがあります。", col3X + 24, warnBoxY + 48);
            g2.drawString("", col3X + 24, warnBoxY + 72);
            g2.drawString("", col3X + 24, warnBoxY + 96);

            // --- (F) 最下部 Microsoft フッター ---
            g2.setColor(new Color(24, 30, 40));
            g2.fillRect(0, h - 26, w, 26);
            g2.setColor(new Color(190, 200, 215));
            g2.setFont(FONT_UI_11);
            g2.drawString("© Microsoft 2026  |  Microsoft Defender Security Intelligence  |  プライバシーとCookie  |  利用規約  |  セキュリティ診断ID: FW0-2026-1030", 24, h - 9);

            g2.dispose();
            return img;
        }

        private void drawCardFrame(Graphics2D g2, int x, int y, int w, int h, String title) {
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(x, y, w, h, 6, 6);
            g2.setColor(new Color(210, 216, 224));
            g2.drawRoundRect(x, y, w, h, 6, 6);
            g2.setColor(new Color(0, 92, 168));
            g2.fillRect(x, y, w, 28);
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_UI_12_B);
            g2.drawString(title, x + 12, y + 19);
        }

        private void drawMiniBar(Graphics2D g2, int x, int y, int w, String label, double ratio, Color barColor) {
            g2.setFont(FONT_UI_11);
            g2.setColor(new Color(60, 60, 60));
            g2.drawString(label, x, y + 10);
            g2.setColor(new Color(225, 230, 238));
            g2.fillRect(x, y + 14, w, 9);
            g2.setColor(barColor);
            g2.fillRect(x, y + 14, (int) (w * ratio), 9);
        }

        /**
         * 2. 中央奥：Windows Defender セキュリティ センター大ウィンドウ
         */
        private void drawDefenderSecurityCenterWindow(Graphics2D g2, int shakeX, int shakeY) {
            int w = 820;
            int h = 490;
            int x = (screenW - w) / 2 + 55 + shakeX;
            int y = (screenH - h) / 2 - 15 + shakeY;

            g2.setColor(new Color(0, 0, 0, 110));
            g2.fillRect(x + 8, y + 8, w, h);

            g2.setColor(Color.WHITE);
            g2.fillRect(x, y, w, h);

            g2.setColor(new Color(0, 103, 184));
            g2.fillRect(x, y, w, 34);
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_UI_13_B);
            g2.drawString("🛡  Windows Defender セキュリティ センター", x + 14, y + 22);

            g2.setColor(new Color(232, 17, 35));
            g2.fillRect(x + w - 44, y, 44, 34);
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_UI_16_B);
            g2.drawString("×", x + w - 27, y + 23);

            int sideW = 200;
            g2.setColor(new Color(242, 244, 248));
            g2.fillRect(x, y + 34, sideW, h - 34);
            g2.setColor(new Color(220, 224, 230));
            g2.drawLine(x + sideW, y + 34, x + sideW, y + h);

            String[] menus = {
                    "🏠  ホーム",
                    "🛡  ウイルスと脅威の防止",
                    "👤  アカウントの保護",
                    "🔥  ファイアウォールとネットワーク",
                    "💻  デバイスのセキュリティ"
            };
            g2.setFont(FONT_UI_12_B);
            for (int i = 0; i < menus.length; i++) {
                int my = y + 56 + i * 42;
                if (i == 1) {
                    g2.setColor(new Color(215, 230, 248));
                    g2.fillRect(x + 1, my - 16, sideW - 1, 34);
                    g2.setColor(new Color(0, 103, 184));
                    g2.fillRect(x, my - 16, 4, 34);
                }
                g2.setColor(new Color(35, 35, 35));
                g2.drawString(menus[i], x + 16, my + 5);
            }

            int mainX = x + sideW + 24;
            int mainW = w - sideW - 48;
            g2.setColor(new Color(209, 52, 56));
            g2.fillRect(mainX, y + 50, mainW, 62);

            g2.setColor(Color.WHITE);
            g2.setFont(FONT_UI_18_B);
            g2.drawString("× 脅威が検出されました — 直ちに対処が必要です", mainX + 18, y + 78);
            g2.setFont(FONT_UI_12);
            g2.drawString("システムスキャンにより、有害な可能性のあるスパイウェアとトロイの木馬が検出されました。", mainX + 18, y + 101);

            int pct = 65 + ((tickCount / 4) % 34);
            g2.setColor(new Color(30, 30, 30));
            g2.setFont(FONT_UI_13_B);
            g2.drawString("クイック スキャン実行中... (" + pct + "% 完了)  C:\\Windows\\System32\\", mainX, y + 140);

            g2.setColor(new Color(225, 230, 238));
            g2.fillRect(mainX, y + 148, mainW, 14);
            g2.setColor(new Color(209, 52, 56));
            g2.fillRect(mainX, y + 148, (int) (mainW * (pct / 100.0)), 14);

            int tableY = y + 178;
            g2.setColor(new Color(248, 249, 251));
            g2.fillRect(mainX, tableY, mainW, 130);
            g2.setColor(new Color(215, 220, 228));
            g2.drawRect(mainX, tableY, mainW, 130);

            String[][] threats = {
                    {"! Trojan:Win32/Banker.Injector!mt", "リスク: 重大 (Severe)", "状態: アクティブ（隔離待ち）"},
                    {"! Spyware:Win32/KeyLogger.Sim", "リスク: 高 (High)", "状態: 通信ブロック中"},
                    {"! Adware:Win32/TrackRegistry.FW0", "リスク: 中 (Medium)", "状態: 検出"}
            };
            for (int i = 0; i < threats.length; i++) {
                int ry = tableY + 30 + i * 36;
                g2.setColor(new Color(196, 43, 28));
                g2.setFont(FONT_UI_13_B);
                g2.drawString(threats[i][0], mainX + 14, ry);
                g2.setColor(new Color(60, 60, 60));
                g2.setFont(FONT_UI_12);
                g2.drawString(threats[i][1], mainX + 265, ry);
                g2.setColor(new Color(0, 90, 158));
                g2.drawString(threats[i][2], mainX + 410, ry);
            }

            g2.setColor(new Color(0, 103, 184));
            g2.drawRect(x, y, w, h);
        }

        /**
         * 3. Windows XP風のコマンドプロンプト画面（これだけWindows XP風・常時スクロール）
         */
        private void drawWindowsXpCommandPrompt(Graphics2D g2) {
            int x = cmdX;
            int y = cmdY;
            int w = cmdW;
            int h = cmdH;

            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRect(x + 6, y + 6, w, h);

            g2.setColor(new Color(0, 84, 227));
            g2.fillRoundRect(x, y, w, h, 8, 8);

            g2.setColor(new Color(15, 96, 248));
            g2.fillRoundRect(x + 2, y + 2, w - 4, 28, 6, 6);
            g2.setColor(new Color(95, 165, 255));
            g2.drawLine(x + 6, y + 3, x + w - 6, y + 3);

            g2.setColor(Color.BLACK);
            g2.fillRect(x + 8, y + 7, 18, 15);
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_UI_11);
            g2.drawString("C:\\", x + 9, y + 18);

            g2.setFont(FONT_TAHOMA_13_B);
            g2.setColor(Color.WHITE);
            g2.drawString("C:\\WINDOWS\\system32\\cmd.exe", x + 32, y + 20);

            g2.setColor(new Color(224, 67, 47));
            g2.fillRoundRect(x + w - 26, y + 6, 20, 19, 3, 3);
            g2.setColor(Color.WHITE);
            g2.drawRoundRect(x + w - 26, y + 6, 20, 19, 3, 3);
            g2.drawString("×", x + w - 20, y + 20);

            int consoleX = x + 5;
            int consoleY = y + 30;
            int consoleW = w - 10;
            int consoleH = h - 35;

            g2.setColor(Color.BLACK);
            g2.fillRect(consoleX, consoleY, consoleW, consoleH);

            Shape oldClip = g2.getClip();
            g2.setClip(consoleX, consoleY, consoleW, consoleH);
            g2.setFont(FONT_CONSOLAS_13);

            int lineY = consoleY + 18;
            for (int i = 0; i < cmdLines.size(); i++) {
                String line = cmdLines.get(i);
                if (line.contains("CRITICAL") || line.contains("BLOCKED") || line.contains("Threat")) {
                    g2.setColor(new Color(255, 100, 100));
                } else if (i < 4) {
                    g2.setColor(new Color(215, 215, 215));
                } else {
                    g2.setColor(new Color(65, 245, 95));
                }
                g2.drawString(line, consoleX + 8, lineY);
                lineY += 16;
            }
            g2.setClip(oldClip);
        }

        /**
         * 4用キャッシュ生成：中央の「Windows Defender - セキュリティ警告」ウィンドウ画像
         */
        private BufferedImage createCenterModalImage(int w, int h) {
            BufferedImage img = new BufferedImage(w + 12, h + 12, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // ドロップシャドウ
            g2.setColor(new Color(0, 0, 0, 130));
            g2.fillRoundRect(8, 8, w, h, 8, 8);

            // 本体白背景
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);

            // 赤いヘッダー
            g2.setColor(new Color(196, 43, 28));
            g2.fillRect(0, 0, w, 42);
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_UI_14_B);
            g2.drawString("⚠  Windows Defender - セキュリティ警告 (エラー #DW6VB36)", 16, 27);
            g2.drawString("×", w - 26, 27);

            // 見出し
            g2.setColor(new Color(196, 43, 28));
            g2.setFont(FONT_UI_16_B);
            g2.drawString("この PC へのアクセスはセキュリティ上の理由でブロックされました", 22, 76);

            // 本文
            g2.setColor(new Color(30, 30, 30));
            g2.setFont(FONT_UI_13);
            String[] lines = {
                    "お使いのコンピュータでスパイウェアの活動とシステムファイルの破損が",
                    "検出されました。個人データや認証情報の漏洩を防ぐため、現在のセッションは",
                    "一時的に保護ロックされています。",
                    "",
                    "■ 検出された脅威: Trojan:Win32/CryptInject!ml / Spyware.Stealer",
                    "※ 処理が完了するまで、このウィンドウを閉じたり電源を切らないでください。",
                    ""
            };
            int ly = 106;
            for (String line : lines) {
                if (line.startsWith("■")) {
                    g2.setColor(new Color(180, 30, 20));
                    g2.setFont(FONT_UI_13_B);
                } else {
                    g2.setColor(new Color(35, 35, 35));
                    g2.setFont(FONT_UI_13);
                }
                g2.drawString(line, 22, ly);
                ly += 21;
            }

            // 下部フッター＆ボタン
            g2.setColor(new Color(240, 242, 245));
            g2.fillRect(1, h - 54, w - 2, 53);
            g2.setColor(new Color(215, 218, 224));
            g2.drawLine(1, h - 54, w - 2, h - 54);

            g2.setColor(new Color(0, 103, 184));
            g2.fillRoundRect(w - 275, h - 42, 160, 32, 4, 4);
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_UI_12_B);
            g2.drawString("今すぐスキャンして修復", w - 260, h - 21);

            g2.setColor(Color.WHITE);
            g2.fillRoundRect(w - 104, h - 42, 88, 32, 4, 4);
            g2.setColor(new Color(160, 165, 172));
            g2.drawRoundRect(w - 104, h - 42, 88, 32, 4, 4);
            g2.setColor(new Color(50, 50, 50));
            g2.drawString("キャンセル", w - 88, h - 21);

            // 赤枠
            g2.setColor(new Color(196, 43, 28));
            g2.drawRect(0, 0, w - 1, h - 1);
            g2.drawRect(1, 1, w - 3, h - 3);

            g2.dispose();
            return img;
        }

        /**
         * 4. 中央手前の警告ウィンドウを「ふわふわ拡大縮小（ズームイン・アウト）」して描画
         */
        private void drawZoomingCenterModal(Graphics2D g2, int shakeX, int shakeY) {
            // サイン波で 0.93倍 〜 1.09倍 に滑らかに拡大縮小（画像キャッシュの描画なので超高速）
            double scale = 1.01 + Math.sin(tickCount * 0.11) * 0.08;
            int scaledW = (int) Math.round(centerModalCache.getWidth() * scale);
            int scaledH = (int) Math.round(centerModalCache.getHeight() * scale);

            int centerX = screenW / 2 - 10 + shakeX;
            int centerY = screenH / 2 + 45 + shakeY;
            int drawX = centerX - scaledW / 2;
            int drawY = centerY - scaledH / 2;

            g2.drawImage(centerModalCache, drawX, drawY, scaledW, scaledH, null);
        }

        /**
         * 5用キャッシュ生成：右下の「緊急セキュリティ隔離モニター」ウィンドウ画像
         */
        private BufferedImage createFloatingCardImage(int w, int h) {
            BufferedImage img = new BufferedImage(w + 10, h + 10, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRoundRect(6, 6, w, h, 10, 10);

            g2.setColor(new Color(14, 38, 82));
            g2.fillRoundRect(0, 0, w, h, 10, 10);

            g2.setColor(new Color(220, 53, 69));
            g2.fillRoundRect(0, 0, w, 36, 10, 10);
            g2.fillRect(0, 18, w, 18);

            g2.setColor(Color.WHITE);
            g2.setFont(FONT_UI_13_B);
            g2.drawString("🛡 Windows セキュリティ 緊急隔離アラート", 14, 24);

            g2.setFont(FONT_UI_15_B);
            g2.setColor(new Color(255, 220, 80));
            g2.drawString("⚠ 重大な脅威をリアルタイム隔離中...", 16, 64);

            g2.setFont(FONT_UI_12);
            g2.setColor(new Color(230, 238, 250));
            g2.drawString("・検出ウイルス: Trojan:Win32/Banker.Injector!mt", 16, 90);
            g2.drawString("・ファイアウォール保護レベル: 最高 (外部通信遮断)", 16, 112);
            g2.drawString("・システム保護: 電源を切らずにお待ちください", 16, 134);

            g2.setColor(new Color(209, 52, 56));
            g2.fillRoundRect(16, 146, w - 32, 26, 6, 6);
            g2.setColor(Color.WHITE);
            g2.setFont(FONT_UI_12_B);
            g2.drawString("⚠ セキュリティ保護ロック作動中", 64, 164);

            g2.setColor(new Color(110, 175, 255));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);

            g2.dispose();
            return img;
        }

        /**
         * 5. 右下の緊急隔離モニターも「ふわふわ拡大縮小」して描画
         */
        private void drawZoomingFloatingCard(Graphics2D g2, int shakeX, int shakeY) {
            // 中央ウィンドウとは少し位相をずらして 0.92倍 〜 1.08倍 にふわふわ拡大縮小
            double scale = 1.0 + Math.cos(tickCount * 0.13) * 0.08;
            int scaledW = (int) Math.round(floatingCardCache.getWidth() * scale);
            int scaledH = (int) Math.round(floatingCardCache.getHeight() * scale);

            int centerX = screenW - FLOAT_CARD_W / 2 - 48 + shakeX;
            int centerY = screenH - FLOAT_CARD_H / 2 - 58 + shakeY;
            int drawX = centerX - scaledW / 2;
            int drawY = centerY - scaledH / 2;

            g2.drawImage(floatingCardCache, drawX, drawY, scaledW, scaledH, null);
        }

        /**
         * 6. 偽マウスカーソル（1つ）の描画
         */
        private void drawFakeMouseCursor(Graphics2D g2, int cx, int cy) {
            if (fakeCursorClickAnim > 0) {
                int r = (11 - fakeCursorClickAnim) * 3;
                g2.setColor(new Color(255, 80, 80, 180));
                g2.drawOval(cx - r, cy - r, r * 2, r * 2);
            }

            Path2D cursorPath = new Path2D.Double();
            cursorPath.moveTo(cx, cy);
            cursorPath.lineTo(cx, cy + 21);
            cursorPath.lineTo(cx + 5, cy + 16);
            cursorPath.lineTo(cx + 9, cy + 25);
            cursorPath.lineTo(cx + 12, cy + 23);
            cursorPath.lineTo(cx + 8, cy + 15);
            cursorPath.lineTo(cx + 15, cy + 15);
            cursorPath.closePath();

            g2.translate(2, 2);
            g2.setColor(new Color(0, 0, 0, 110));
            g2.fill(cursorPath);
            g2.translate(-2, -2);

            g2.setColor(Color.WHITE);
            g2.fill(cursorPath);
            g2.setColor(Color.BLACK);
            g2.draw(cursorPath);
        }
    }
}
