package server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class ClientHandler {
    DataInputStream in;
    DataOutputStream out;
    Server server;
    Socket socket;

    private String nickname;
    private String login;

    public ClientHandler(Server server, Socket socket) {
        try {
            this.server = server;
            this.socket = socket;
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            System.out.println("Client connected " + socket.getRemoteSocketAddress());

            new Thread(() -> {
                try {
                    socket.setSoTimeout(120000);

                    //цикл аутентификации
                    while (true) {
                        String str = in.readUTF();

                        if (str.startsWith("/reg ")) {
                            String[] token = str.split("\\s");
                            if (token.length < 4) {
                                continue;
                            }
                            boolean b = server.getAuthService()
                                    .registration(token[1], token[2], token[3]);
                            if (b) {
                                sendMsg("/regok");
                            } else {
                                sendMsg("/regno");
                            }
                        }

                        if (str.startsWith("/auth ")) {
                            String[] token = str.split("\\s");
                            if (token.length < 3) {
                                continue;
                            }
                            String newNick = server.getAuthService()
                                    .getNicknameByLoginAndPassword(token[1], token[2]);
                            if (newNick != null) {
                                login = token[1];
                                if (!server.isLoginAuthenticated(login)) {
                                    nickname = newNick;
                                    sendMsg("/authok " + newNick);
                                    server.subscribe(this);
                                    socket.setSoTimeout(0);
                                    //==============//
                                    sendMsg(SQLHandler.getMessageForNick(nickname));
                                    //==============//
                                    break;
                                } else {
                                    sendMsg("С этим логином уже вошли в чат");
                                }
                            } else {
                                sendMsg("Неверный логин / пароль");
                            }
                        }
                    }

                    //цикл работы
                    while (true) {
                        String str = in.readUTF();
                        if (str.startsWith("/")) {
                            if (str.equals("/end")) {
                                sendMsg("/end");
                                break;
                            }
                            if (str.startsWith("/w ")) {
                                String[] token = str.split("\\s", 3);
                                if (token.length < 3) {
                                    continue;
                                }
                                server.privateMsg(this, token[1], token[2]);
                            }

                            //==============//
                            if (str.startsWith("/chnick ")) {
                                String[] token = str.split(" ", 2);
                                if (token.length < 2) {
                                    continue;
                                }
                                if (token[1].contains(" ")) {
                                    sendMsg("Ник не может содержать пробелов");
                                    continue;
                                }
                                if (server.getAuthService().changeNick(this.nickname, token[1])) {
                                    sendMsg("/yournickis " + token[1]);
                                    sendMsg("Ваш ник изменен на " + token[1]);
                                    this.nickname = token[1];
                                    server.broadcastClientList();
                                } else {
                                    sendMsg("Не удалось изменить ник. Ник " + token[1] + " уже существует");
                                }
                            }
                            //==============//
                        } else {
                            server.broadcastMsg(this, str);
                        }
                    }

                } catch (SocketTimeoutException e) {
                    sendMsg("/end");
                    System.out.println("Client disconnected by timeout");
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    server.unsubscribe(this);
                    System.out.println("Client disconnected " + socket.getRemoteSocketAddress());
                    try {
                        socket.close();
                        in.close();
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getNickname() {
        return nickname;
    }

    public String getLogin() {
        return login;
    }
}
