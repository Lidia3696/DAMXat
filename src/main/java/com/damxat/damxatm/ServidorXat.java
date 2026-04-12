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
import java.util.logging.Level;
import java.util.logging.Logger;

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

    //nicks de clientes
    //static para compartirlo entre conexiones
    private static final HashMap<String, PrintWriter> usersHashMap = new HashMap<>();
    private String nickClient;

    public ServidorXat(int puerto) {
        this.puerto = puerto;
    }

    public ServidorXat(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            //crear socket, aqui solo hace falta el puerto
            this.serverSocket = new ServerSocket(this.puerto);
            System.out.println("[Servidor] Escoltant al port " + this.puerto + "...");

            while (true) {
                //espera a que un cliente se conecte con accept
                //el hilo está parado hasta que el cliente se conecta
                Socket clientSocket = this.serverSocket.accept();
                System.out.println("[Servidor] Connexió establerta amb el client.");

                //crea un hilo para recibir mensajes del cliente con la fucnion de recibir mensajse
                Thread tClient = new Thread(() -> {
                    ServidorXat gestor = new ServidorXat(clientSocket);
                    //llama a funcion que crea y asigna todos los componentes necesarios
                    gestor.establecerStreamSocket();
                    if (gestor.registrarNick()) {
                        gestor.recibeMensajes();
                    }
                });
                tClient.setDaemon(true);
                tClient.start();

            }

        } catch (Exception e) {
            System.out.println("[Servidor] Error: " + e.getMessage());
        }
    }

    void establecerStreamSocket() {
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

    boolean registrarNick() {
        try {
            //esto lee lo que le llega
            String nick = this.br.readLine();

            // COMPROBACIONES
            //comproacion si esta vacio
            if (nick == null || nick.isBlank()) {
                enviaTxt("Nick invàlid. Connexió tancada.");
                return false;
            }

            //comprobacion que no este repeteido
            if (usersHashMap.containsKey(nick)) {
                enviaTxt("NICK_REPETIT");
                return false;
            }

            //asignar nueo nick al nick de la clase
            this.nickClient = nick;

            //un printwriter sirve para imprimit texto con formato
            //aqui se crea pasandole el outputstream con autoflush en true
            //PrintWriter(OutputStream out, boolean autoFlush) 	Creates a new PrintWriter from an existing OutputStream.
            PrintWriter pw = new PrintWriter(this.out, true);

            //añadir el nick yel printiriter al hasmap del servidor, como es static se escribe tal cual
            usersHashMap.put(nick, pw);

            System.out.println("[Servidor] Nou usuari registrat amb nick " + nick);

            //el cliente lee el string y lo usa para evaluar si se ha conecntado o no
            enviaTxt("NICK_OK");
            //comunicar a todos los clientes que alguien se ha conectado
            enviarMensajeATodos(nick + " s'ha connectat.", nick);


        } catch (IOException e) {
            return false;
        }

        return true;
    }

    //el nick excluido es el nick al que no se le quiere enviar el mensaje, que suele ser el del usuario que lo envía
    void enviarMensajeATodos(String mensaje, String nickExcluido) {

        //entra al hashmap con syncronized por si de mientras se une otro usuario
        synchronized (usersHashMap) {
            //itera por el hashmap usando el elemento entry, el nick del usuario con su printwriter
            //por cada entry del entryset del hashmap...
            for (HashMap.Entry<String, PrintWriter> entry : usersHashMap.entrySet()) {
                //si el nick excluido no coincide con el nick del entry
                if (!entry.getKey().equals(nickExcluido)) {
                    //se le imprime el mensaje en cuestión
                    entry.getValue().println(mensaje);
                }
            }
        }

    }

    //funcion que recoje el texto del teclado
    //luego llama a enviatxt que comprueba que se haya enviado bien
    void enviarMensaje(String prefix) {
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

    //el bool es para comprobar que se ha enviado bien, por eso esta dentro de un try y el catch devuelve false
    public boolean enviaTxt(String mensaje) {
        if (this.socket == null || !this.socket.isConnected()) {
            return false;
        }
        try {
            //escribe el mensaje en el buffered writer (output)
            this.bw.write(mensaje);
            this.bw.newLine();
            this.bw.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    //funcion del hilo, recibe mensajes de los clientes
    void recibeMensajes() {
        String mensaje;
        try {
            while ((mensaje = this.br.readLine()) != null) {
                if (mensaje.equalsIgnoreCase("adeu") || mensaje.equalsIgnoreCase("exit")) {
                    System.out.println("L'usuari " + nickClient + " s'ha desconnectat.");
                    enviarMensajeATodos("[Servidor] L'usuari " + nickClient + " ha sortit del xat.", null);
                    //eliina el nich del hshmp
                    eliminarUsuario();
                }
                System.out.println("[" + nickClient + "] " + mensaje);
                enviarMensajeATodos("[" + nickClient + "] " + mensaje, nickClient);
            }
        } catch (IOException e) {
            System.out.println("[Servidor] Connexió tancada.");
            eliminarUsuario();
        }
    }

    void eliminarUsuario() {
        if (nickClient != null) {
            synchronized (usersHashMap) {
                usersHashMap.remove(nickClient);
            }
            System.out.println("[Servidor] Usuari eliminat: " + nickClient);
        }
    }

    Socket getSocket() {
        return this.socket;
    }

    //funcion para cerrar el server de manera correcta
    //si no es null, se cierran
    void cerrar() throws IOException {
        if (this.br != null) {
            this.br.close();
        }
        if (this.socket != null) {
            this.socket.close();
        }
        if (this.serverSocket != null) {
            this.serverSocket.close();
        }
    }
}
