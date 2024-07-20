package game;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import game.entities.Player;
import game.entities.PlayerMP;
import game.gfx.Colours;
import game.gfx.Screen;
import game.gfx.SpriteSheet;
import game.level.Level;
import game.net.GameClient;
import game.net.GameServer;
import game.net.packets.Packet00Login;

public class Game extends Canvas implements Runnable {

	public static Scanner inp;
	public static InetAddress inet;
	public static String url;

	private static final long serialVersionUID = 1L;

	public static final int WIDTH = 300;
	public static final int HEIGHT = WIDTH / 12 * 9;
	public static final int SCALE = 4;
	public static final String NAME = "Game";
	public static Game game;

	public JFrame frame;

	private Thread thread;

	public boolean running = false;
	public int tickCount = 0;

	private BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
	private int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();

	private int[] colours = new int[6 * 6 * 6];

	private Screen screen;
	public InputHandler input;
	public WindowHandler windowHandler;
	public Level level;
	public Player player;

	public GameClient socketClient;
	public GameServer socketServer;

	private String address;

	private boolean debug = true;

	public String username;

	public int test = 0;

	public Game() {
		setMinimumSize(new Dimension(WIDTH * SCALE, HEIGHT * SCALE));
		setMaximumSize(new Dimension(WIDTH * SCALE, HEIGHT * SCALE));
		setPreferredSize(new Dimension(WIDTH * SCALE, HEIGHT * SCALE));

		frame = new JFrame(NAME);

		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setLayout(new BorderLayout());

		frame.add(this, BorderLayout.CENTER);
		frame.pack();

		frame.setResizable(false);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	@SuppressWarnings("static-access")
	public void init() {
		Game.game = this;
		int index = 0;
		for (int r = 0; r < 6; r++) {
			for (int g = 0; g < 6; g++) {
				for (int b = 0; b < 6; b++) {
					int rr = (r * 255 / 5);
					int gg = (g * 255 / 5);
					int bb = (b * 255 / 5);

					colours[index++] = rr << 16 | gg << 8 | bb;
				}
			}
		}

		screen = new Screen(WIDTH, HEIGHT, new SpriteSheet("/sprite_sheet.png"));
		input = new InputHandler(this);
		windowHandler = new WindowHandler(this);
		level = new Level("/levels/water_test_level.png");
		UIManager um = new UIManager();
		um.put("OptionPane.messageForeground", Color.black);
		username = JOptionPane.showInputDialog(this, "Please enter a username!");
		if (username.length() > 16) {
			boolean validUsername = false;
			um.put("OptionPane.messageForeground", Color.red);
			while (true != validUsername) {
				username = JOptionPane.showInputDialog(this, "The username can not be longer than 16 characters!");
				if (username.length() <= 16) {
					validUsername = true;
				}
			}
		}
		player = new PlayerMP(level, 100, 100, input, username, null, -1);
		level.addEntity(player);
		Packet00Login loginPacket = new Packet00Login(player.getUsername(), player.x, player.y);
		if (socketServer != null) {
			socketServer.addConnection((PlayerMP) player, loginPacket);
		}
		loginPacket.writeData(socketClient);
	}

	@Override
	public void run() {
		long lastTime = System.nanoTime();
		double nsPerTick = 1000000000D / 60D;

		int ticks = 0;
		int frames = 0;

		long lastTimer = System.currentTimeMillis();
		double delta = 0;

		init();

		while (running) {
			long now = System.nanoTime();
			delta += (now - lastTime) / nsPerTick;
			lastTime = now;

			boolean shouldRender = true;

			while (delta >= 1) {
				ticks++;
				tick();
				delta -= 1;
				shouldRender = true;
			}

			try {
				Thread.sleep(2);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (shouldRender) {
				frames++;
				render();
			}

			if (System.currentTimeMillis() - lastTimer >= 1000) {
				lastTimer += 1000;
				debug(DebugLevel.INFO, "frames: " + frames + ", " + "ticks: " + ticks);
				frames = 0;
				ticks = 0;
			}
		}
	}

	public void tick() {
		tickCount++;
		level.tick();
	}

	@SuppressWarnings("unused")
	public void render() {
		BufferStrategy bs = getBufferStrategy();
		if (bs == null) {
			createBufferStrategy(3);
			return;
		}

		int xOffset = player.x - (screen.width / 2);
		int yOffset = player.y - (screen.height / 2);

		level.renderTiles(screen, xOffset, yOffset);

		for (int x = 0; x < level.width; x++) {
			int colour = Colours.get(-1, -1, -1, 000);
			if (x % 10 == 0 && x != 0) {
				colour = Colours.get(-1, -1, -1, 500);
			}
		}

		level.renderEntities(screen);
//		System.out.println(level.getEntities());
		for (int y = 0; y < screen.height; y++) {
			for (int x = 0; x < screen.width; x++) {
				int colourCode = screen.pixels[x + y * screen.width];
				if (colourCode < 255)
					pixels[x + y * WIDTH] = colours[colourCode];
			}
		}

		Graphics g = bs.getDrawGraphics();
		g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
		g.dispose();
		bs.show();

	}

	public static void main(String[] args) throws UnknownHostException {
		new Game().start();
	}

	@SuppressWarnings("static-access")
	private synchronized void start() throws UnknownHostException {
		running = true;
		thread = new Thread(this, NAME + "_main");
		thread.start();
		InetAddress myIP = InetAddress.getLocalHost();
		UIManager um = new UIManager();
		um.put("OptionPane.messageForeground", Color.black);
		if (JOptionPane.showConfirmDialog(this, "Do you want to host a server?") == 0) {
			test = 0;
			socketServer = new GameServer(this);
			socketServer.start();
			socketClient = new GameClient(this, myIP.getHostAddress().toString());
			socketClient.start();
		} else {
			test = 1;
			boolean validAddress = false;
			um.put("OptionPane.messageForeground", Color.black);
			address = JOptionPane.showInputDialog(this, "Please enter a server IP address!");
			if (isValidInet4Address(address)) {
				validAddress = true;
			}
			if (!isValidInet4Address(address)) {
				while (validAddress == false) {
					um.put("OptionPane.messageForeground", Color.red);
					address = JOptionPane.showInputDialog(this, "Please enter a valid IP address!");
					if (isValidInet4Address(address)) {
						validAddress = true;
					}
				}
			}

			socketClient = new GameClient(this, address);
			socketClient.start();
		}
	}

	public synchronized void stop() {
		running = false;
	}

	public void debug(DebugLevel level, String msg) {
		switch (level) {
		default:
		case INFO:
			if (debug) {
//				System.out.println("[" + NAME + "] " + msg);
			}
			break;
		case WARNING:
//			System.out.println("[" + NAME + "] [WARNING] " + msg);
			break;
		case SEVERE:
//			System.out.println("[" + NAME + "] [SEVERE] " + msg);
			this.stop();
			break;
		}
	}

	public static enum DebugLevel {
		INFO, WARNING, SEVERE;
	}

	public static boolean isValidInet4Address(String ip) {
		try {
			InetAddress ipAddress1 = InetAddress.getByName(ip);
			String ipAddress2 = ipAddress1.getHostAddress();
			return Inet4Address.getByName(ip).getHostAddress().equals(ipAddress2);
		} catch (UnknownHostException ex) {
			return false;
		}
	}

}