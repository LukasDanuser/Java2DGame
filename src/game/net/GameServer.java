package game.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

import game.Game;
import game.entities.PlayerMP;
import game.net.packets.Packet;
import game.net.packets.Packet.PacketTypes;
import game.net.packets.Packet00Login;
import game.net.packets.Packet01Disconnect;
import game.net.packets.Packet02Move;

public class GameServer extends Thread {

	private DatagramSocket socket;
	private Game game;
	private List<PlayerMP> connectedPlayers = new ArrayList<PlayerMP>();
	public List<String> connectedUsernames = new ArrayList<String>();
	private String username;
	@SuppressWarnings("unused")
	private Packet packet1 = null;

	public GameServer(Game game) {
		this.game = game;
		try {
			this.socket = new DatagramSocket(6565);
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	public List<PlayerMP> getConnectedPlayers() {
		return connectedPlayers;
	}

	public List<String> getConnectedUsernames() {
		return connectedUsernames;
	}

	@Override
	public void run() {
		while (true) {
			byte[] data = new byte[1000000];
			DatagramPacket packet = new DatagramPacket(data, data.length);
			try {
				socket.receive(packet);
			} catch (IOException e) {
				e.printStackTrace();
			}
			this.parsePacket(packet.getData(), packet.getAddress(), packet.getPort());
		}
	}

	private void parsePacket(byte[] data, InetAddress address, int port) {
		String message = new String(data).trim();
		PacketTypes type = Packet.lookupPacket(message.substring(0, 2));
		Packet packet = null;
		switch (type) {
		default:
		case INVALID:
			break;
		case LOGIN:
			packet = new Packet00Login(data);
			System.out.println("[" + address.getHostName() + ":" + port + "] " + ((Packet00Login) packet).getUsername()
					+ " has connected...");
			PlayerMP player = new PlayerMP(game.level, 100, 100, ((Packet00Login) packet).getUsername(), address, port);
			this.addConnection(player, (Packet00Login) packet);
			break;
		case DISCONNECT:
			packet = new Packet01Disconnect(data);
			System.out.println("[" + address.getHostName() + ":" + port + "] "
					+ ((Packet01Disconnect) packet).getUsername() + " has left...");
			this.removeConnection((Packet01Disconnect) packet);
			break;
		case MOVE:
			packet = new Packet02Move(data);
			this.handleMove(((Packet02Move) packet));
		}
	}

	public void addConnection(PlayerMP player, Packet00Login packet) {
		boolean alreadyConnected = false;
		Packet00Login packetLogin = packet;
		for (PlayerMP p : this.connectedPlayers) {
			packet = packetLogin;
			if (player.getUsername().equalsIgnoreCase(p.getUsername())) {
				if (p.ipAddress == null) {
					p.ipAddress = player.ipAddress;
				}

				if (p.port == -1) {
					p.port = player.port;
				}
				alreadyConnected = true;
			} else {
				sendData(packet.getData(), p.ipAddress, p.port);
				packet = new Packet00Login(p.getUsername(), p.x, p.y);
				sendData(packet.getData(), player.ipAddress, player.port);

			}
			packetLogin = packet;
		}

		if (alreadyConnected == false) {
			this.connectedPlayers.add(player);
			this.connectedUsernames.add(player.getUsername());
		}
		System.out.println(getConnectedUsernames());
	}

	public void removeConnection(Packet01Disconnect packet) {
		if (this.username != null) {
			packet.setUsername(username);
		}
		this.connectedPlayers.remove(getPlayerMPIndex(packet.getUsername()));
		this.connectedUsernames.remove(getPlayerMPIndex(packet.getUsername()));
		packet.writeData(this);
	}

	public PlayerMP getPlayerMP(String username) {
		for (PlayerMP player : this.connectedPlayers) {
			if (player.getUsername().equals(username)) {
				return player;
			}
		}
		return null;
	}

	public int getPlayerMPIndex(String username) {
		int index = 0;
		for (PlayerMP player : this.connectedPlayers) {
			if (player.getUsername().equals(username)) {
				break;
			}
			index++;
		}
		return index;
	}

	public void sendData(byte[] data, InetAddress ipAddress, int port) {
		DatagramPacket packet = new DatagramPacket(data, data.length, ipAddress, port);
		try {
			this.socket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendDataToAllClients(byte[] data) {
		for (PlayerMP p : connectedPlayers) {
			sendData(data, p.ipAddress, p.port);
		}
	}

	private void handleMove(Packet02Move packet) {
		if (this.username != null) {
			packet.setUsername(this.username);
			packet1 = new Packet02Move(packet.getData());
			sendDataToAllClients(packet.getData());
			packet1 = new Packet02Move(username, packet.getX(), packet.getY(), packet.getNumSteps(), packet.isMoving(),
					packet.getMovingDir());
			sendDataToAllClients(packet.getData());
		}
		if (getPlayerMP(packet.getUsername()) != null) {
			int index = getPlayerMPIndex(packet.getUsername());
			PlayerMP player = this.connectedPlayers.get(index);
			player.x = packet.getX();
			player.y = packet.getY();
			player.setMoving(packet.isMoving());
			player.setMovingDir(packet.getMovingDir());
			player.setNumSteps(packet.getNumSteps());
			packet.writeData(this);
		}
	}
}