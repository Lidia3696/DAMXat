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
    private int puerto;
    
   
    

    //nicks de clientes
    //static para compartirlo entre conexiones
    private static final HashMap<String, PrintWriter> usersHashMap = new HashMap<>();
    private String nickClient;
    private Object mapaUsuarios;

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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    boolean registrarNick() {
        try {
            //esto lee lo que le llega
            String nick = this.br.readLine();
            if (nick != null) nick = nick.trim();

            // COMPROBACIONES
            //comproacion si esta vacio
            if (nick == null || nick.isBlank()) {
                //crear pw temporal para escribir mensaje de error
                PrintWriter pwTemp = new PrintWriter(this.socket.getOutputStream(), true);
                pwTemp.println("Nick invàlid. Connexió tancada.");
                return false;
            }
            
            //pillar pw del hashmap
            synchronized (usersHashMap) {
                if (usersHashMap.containsKey(nick)) {
                    PrintWriter pwTemp = new PrintWriter(this.socket.getOutputStream(), true);
                    pwTemp.println("NICK_REPETIT");

                    return false;
                }

                //asignar nueo nick al nick de la clase
                this.nickClient = nick;
                //un printwriter sirve para imprimit texto con formato
                //aqui se crea pasandole el outputstream con autoflush en true
                //PrintWriter(OutputStream out, boolean autoFlush) 	Creates a new PrintWriter from an existing OutputStream.
                PrintWriter pw = new PrintWriter(this.socket.getOutputStream(), true);
                
                //añadir el nick yel printiriter al hasmap del servidor, como es static se escribe tal cual
                usersHashMap.put(nick, pw);
            }

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


    //el bool es para comprobar que se ha enviado bien, por eso esta dentro de un try y el catch devuelve false
    public boolean enviaTxt(String mensaje) {
        if (this.socket == null || !this.socket.isConnected()) return false;
        try {
            PrintWriter pw = usersHashMap.get(this.nickClient);
            if (pw != null) {
                pw.println(mensaje);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    //funcion del hilo, recibe mensajes de los clientes
    void recibeMensajes() {
        String mensaje;
        try {
            //leeMOS líneas enviadas por el cliente mientras la conexión esté abierta
            while ((mensaje = this.br.readLine()) != null) {   
                
                //para evitar enviar espacios o \r
                mensaje = mensaje.trim();
                
                if (mensaje.isEmpty()) continue;
                                
                // ***** SORTIR ***** (Desconnexió)
                // Comprobamos si el mensaje es el comando oficial /sortir o las alternativas adeu/exit
                if (mensaje.equalsIgnoreCase("/sortir") || mensaje.equalsIgnoreCase("adeu") || mensaje.equalsIgnoreCase("exit")) {                 
                    System.out.println("L'usuari " + nickClient + " s'ha desconnectat.");
                    
                    // Notificamos a TODOS los demás usuarios (el segundo parámetro es null para que NO se excluya a nadie si es necesario, 
                    // aunque el cliente que se va ya está cerrando su lectura).
                    enviarMensajeATodos("[SERVIDOR]: L'usuari " + nickClient + " ha abandonat el xat.", nickClient);
                    
                    // Eliminamos al usuario del HashMap estático para liberar el Nick
                    eliminarUsuario();    
                    // Salimos del bucle while para que el hilo de este cliente finalice
                    break; 
                }

                // ***** LLISTA *****
                // Si el mensaje recibido es exactamente /llista
                if (mensaje.equalsIgnoreCase("/llista")) {
                    //Tenemos un bloque sincronizado para evitar errores si otro usuario se conecta/desconecta justo ahora
                    synchronized (usersHashMap) {
                        
                        // Creamos un String uniendo todas las llaves (Nicks) del HashMap separadas por coma
                        String listaUsuarios = String.join(", ", usersHashMap.keySet());
                        
                        // Obtenemos el PrintWriter específico de este cliente para responderle SOLO a él
                        PrintWriter pw = usersHashMap.get(nickClient);
                        
                        if (pw != null) {
                            // Enviamos la lista formateada según el requisito
                            pw.println("[SERVIDOR]: Usuaris connectats actualment: " + listaUsuarios);
                        }
                    }
                    //usamos continue para saltar el código de abajo y NO enviar esto como chat global
                    continue; 
                }

                //***** PRIVAT ***** /privat <UsuariDesti> <Missatge> 
                // Verificamos si el mensaje empieza por el comando /privat seguido de un espacio
                if (mensaje.startsWith("/privat ")) {
                    // Dividimos el String en máximo 3 partes: "/privat", destino, el resto del mensaje
                    String[] partes = mensaje.split(" ", 3); 
                    
                    // Verificamos que al menos tengamos las 3 partes necesarias
                    if (partes.length >= 3) {
                        String destino = partes[1].trim(); // El Nick del destinatario
                        String contenido = partes[2]; // El contenido del mensaje privado
                        
                        synchronized (usersHashMap) {
                            // Buscamos si el destinatario existe en nuestro "directorio" (HashMap)
                            PrintWriter pwDestino = usersHashMap.get(destino);
                            PrintWriter pwOrigen  = usersHashMap.get(nickClient);

                            
                            if (pwDestino != null) {
                                // Si existe, le escribimos el mensaje con el formato del protocolo
                                pwDestino.println("[Privat de " + nickClient + "]: " + contenido);
                                //confirmar que se envió
                                if (pwOrigen != null) {
                                    pwOrigen.println("[Privat a " + destino + "]: " + contenido);
                                }
                            } else {
                                // Si NO existe, informamos al emisor del error usando su propio PrintWriter
                                if (pwOrigen != null) {
                                    pwOrigen.println("[SERVIDOR]: Error: L'usuari '" + destino + "' no existeix.");
                                }                           
                            }
                        }
                    } else {
                        // Si el usuario escribió mal el comando ej: privat Maria, le damos una ayuda
                        usersHashMap.get(nickClient).println("[SERVIDOR]: Ús incorrecte: /privat <usuari> <missatge>");
                    }
                    
                    continue; 
                }

                // Si el mensaje no empezó por "/", se trata como un texto normal para todos
                // Mostramos en la consola del servidor quién escribió qué
                System.out.println("[" + nickClient + "] " + mensaje);
                
                // Reenviamos el mensaje a todos los conectados con el formato solicitado: [<NomUsuari>]: <Missatge>
                // Enviamos null en el segundo parámetro para que el propio emisor también vea su mensaje en el chat
                enviarMensajeATodos("[" + nickClient + "]: " + mensaje, null); 
            }
        } catch (IOException e) {    
            System.out.println("[Servidor] Connexió tancada per error de " + nickClient);
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

    private void enviarMensajePrivado(String nickClient, String string) {
        throw new UnsupportedOperationException("Not supported yet."); 
    }
}
