package com.damxat.damxatm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class ServidorXat extends Thread {

    private ServerSocket serverSocket;
    private Socket socket;
    private InputStream in;
    private InputStreamReader inr;
    private BufferedReader br;
    private BufferedWriter bw;
    private OutputStream out;
    private OutputStreamWriter outw;
    private int puerto;
    private HashMap<String, PrintWriter> usersHashMap;

    public ServidorXat(int puerto) {
        this.puerto = puerto;
    }

    @Override
    public void run() {
        try {
            //crear socket, aqui solo hace falta el puerto
            this.serverSocket = new ServerSocket(this.puerto);
            System.out.println("[Servidor] Escoltant al port " + this.puerto + "...");
            //espera a que un cliente se conecte con accept
            //el hilo está parado hasta que el cliente se conecta
            this.socket = this.serverSocket.accept();
            System.out.println("[Servidor] Connexió establerta amb el client.");
            //llama a funcion que crea y asigna todos los componentes necesarios
            establecerStreamSocket();

            //crea un hilo para recibir mensajes del cliente con la fucnion de recibir mensajse
            Thread receptor = new Thread(() -> recibeMensajes());
            receptor.setDaemon(true);
            receptor.start();

            //hilo para enviar mensajes
            enviarMensaje("[Servidor]");

        } catch (Exception e) {
            System.out.println("[Servidor] Error: " + e.getMessage());
        }
    }

    //funcion para cerrar el server de manera correcta
    //si no es null, se cierran
    public void cerrar() throws IOException {
        if (this.br != null) this.br.close();
        if (this.socket != null) this.socket.close();
        if (this.serverSocket != null) this.serverSocket.close();
    }

    private void establecerStreamSocket() {
        try {
            this.in = this.socket.getInputStream();
            this.inr = new InputStreamReader(in);
            this.br = new BufferedReader(inr);
            this.out = this.socket.getOutputStream();
            this.outw = new OutputStreamWriter(out);
            this.bw = new BufferedWriter(outw);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean enviaTxt(String mensaje) {
        if (this.socket == null || !this.socket.isConnected()) return false;
        try {
            this.bw.write(mensaje);
            this.bw.newLine();
            this.bw.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    //funcion del hilo
    private void recibeMensajes() {
        String mensaje;
        try {
            while ((mensaje = this.br.readLine()) != null) {
                if (mensaje.equalsIgnoreCase("adeu") || mensaje.equalsIgnoreCase("exit")) {
                    System.out.println("[Client] " + mensaje);
                    System.out.println("[Servidor] Client desconnectat.");
                    cerrar();
                    System.exit(0);
                }
                System.out.println("[Client] " + mensaje);
            }
        } catch (IOException e) {
            System.out.println("[Servidor] Connexió tancada.");
        }
    }

    private void enviarMensaje(String prefix) {
        BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
        String linea;
        System.out.println("Escriu els teus missatges (escriu 'adeu' per sortir):");
        try {
            while ((linea = teclado.readLine()) != null) {
                enviaTxt(linea);
                if (linea.equalsIgnoreCase("adeu") || linea.equalsIgnoreCase("exit")) {
                    cerrar();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            System.out.println("[Servidor] Error llegint del teclat.");
        }
    }
    
    public Socket getSocket() {
        return this.socket;
    }
}