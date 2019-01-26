package bi.two.telegram;

import bi.two.util.MapConfig;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.request.SendMessage;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import static bi.two.util.Log.console;
import static bi.two.util.Log.err;

public class TheBot {
    private final String m_admin;
    private final String m_botToken;
    private TelegramBot m_bot;

    private TelegramBot getBot() {
        if (m_bot == null) {
            // create a new Telegram bot object to start talking with Telegram
            m_bot = new TelegramBot.Builder(m_botToken)
                    //.okHttpClient(client) // You can build bot with custom OkHttpClient, for specific timeouts or interceptors.
                    .build();

            m_bot.setUpdatesListener(new UpdatesListener() {
                @Override public int process(List<Update> updates) {
                    console("UpdatesListener.process() updates.size=" + updates.size());
                    for (Update update : updates) {
                        console(" " + update);

                        Message message = update.message();
                        console("  message=" + message);
                        if (message != null) {
                            Chat chat = message.chat();
                            console("   chat=" + chat);
                            Contact contact = message.contact();
                            console("   contact=" + contact);
                            User from = message.from();
                            console("   from=" + from);
                            String text = message.text();
                            console("   text=" + text);

                            Integer userId = from.id();

//                            Keyboard replyKeyboardMarkup = new ReplyKeyboardMarkup(
//                                    new String[]{"first row button1", "first row button2"},
//                                    new String[]{"second row button1", "second row button2"})
//                                    .oneTimeKeyboard(true)   // optional
//                                    .resizeKeyboard(true)    // optional
//                                    .selective(true);        // optional
//
//                            InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup(
//                                    new InlineKeyboardButton[]{
//                                            new InlineKeyboardButton("url").url("www.google.com"),
//                                            new InlineKeyboardButton("callback_data").callbackData("callback_data"),
//                                            new InlineKeyboardButton("switch_inline_query").switchInlineQuery("switch_inline_query")
//                                    });

                            m_bot.execute(new SendMessage(userId, "echo: " + text + "\nmy time is: " + new Date())
//                                    .replyMarkup(replyKeyboardMarkup)
//                                    .replyMarkup(inlineKeyboard)
//                                    .replyMarkup( new ReplyKeyboardMarkup(new String[]{"test1"}, new String[]{"test2"}, new String[]{"test3"}))
//                                    .replyMarkup(new ReplyKeyboardMarkup(new String[]{"a","b","c"}))
                            );

//                            SendMessage request = new SendMessage(chatId, "text")
//                                    .parseMode(ParseMode.HTML)
//                                    .disableWebPagePreview(true)
//                                    .disableNotification(true)
//                                    .replyToMessageId(1)
//                                    .replyMarkup(new ForceReply());

                        }
                    }
                    return UpdatesListener.CONFIRMED_UPDATES_ALL; // all updates processed
                }
            });
        }
        return m_bot;
    }

    private TheBot(String botToken, String admin) {
        m_botToken = botToken;
        m_admin = admin;
    }

    public void sendMsg(String msg, boolean silent) {
        try {
            SendMessage message = new SendMessage(m_admin, msg);
            if (silent) {
                message.disableNotification(true);
            }
            getBot().execute(message);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                RuntimeException re = (RuntimeException) e;
                Throwable e1 = re.getCause();
                if (e1 instanceof SSLHandshakeException) {
                    SSLHandshakeException she = (SSLHandshakeException) e1;
                    Throwable e2 = she.getCause();
                    String name2 = e2.getClass().getName();
                    if (name2.equals("sun.security.validator.ValidatorException")) {
                        Throwable e3 = e2.getCause();
                        String name3 = e3.getClass().getName();
                        if (name3.equals("sun.security.provider.certpath.SunCertPathBuilderException")) {
                            console("TheBot.sendMsg error: " + e); // light log of expected error
                            return;
                        }
                    }
                }
            }
            err("TheBot.sendMsg error: " + e, e);
        }
    }

    public static void main(String[] args) {
        String file = "cfg" + File.separator + "vary.properties";
        MapConfig config = new MapConfig();
        try {
            config.load(file);
        } catch (IOException e) {
            err("config load error: " + e, e);
            return;
        }

        String botToken = config.getString("telegram");
        String admin = config.getString("admin");
        TheBot theBot = create(botToken, admin);
        if (theBot == null) {
            theBot.sendMsg("I am alive", true);
        } else {
            console("no bot required params in config");
        }
    }

    public static TheBot create(String botToken, String admin) {
        TheBot bot = ((botToken != null) && (admin != null))
                ? new TheBot(botToken, admin)
                : null;
        return bot;
    }

    public static TheBot create(MapConfig config) {
        String botToken = config.getPropertyNoComment("telegram");
        String admin = config.getPropertyNoComment("admin");
        return create(botToken, admin);
    }
}
