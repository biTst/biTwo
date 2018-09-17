package bi.two.telegram;

import bi.two.util.MapConfig;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.*;
import com.pengrad.telegrambot.request.SendMessage;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

import static bi.two.util.Log.console;

public class TheBot {
    public static void main(String[] args) {

        String file = "cfg" + File.separator + "vary.properties";
        MapConfig config = new MapConfig();
        try {
            config.load(file);
        } catch (IOException e) {}

        String botToken = config.getString("telegram");
        String admin = config.getString("admin");

        // create a new Telegram bot object to start talking with Telegram
        final TelegramBot bot = new TelegramBot.Builder(botToken)
                //.okHttpClient(client) // You can build bot with custom OkHttpClient, for specific timeouts or interceptors.
                .build();

        bot.execute(new SendMessage(admin, "I am alive")
                          .disableNotification(true)
        );

        bot.setUpdatesListener(new UpdatesListener() {
            @Override public int process(List<Update> updates) {
                console("UpdatesListener.process() updates.size="+updates.size());
                for (Update update : updates) {
                    console(" " + update);

                    Message message = update.message();
                    console("  message=" + message);
                    if(message != null) {
                        Chat chat = message.chat();
                        console("   chat=" + chat);
                        Contact contact = message.contact();
                        console("   contact=" + contact);
                        User from = message.from();
                        console("   from=" + from);
                        String text = message.text();
                        console("   text=" + text);

                        Integer userId = from.id();

//                        Keyboard replyKeyboardMarkup = new ReplyKeyboardMarkup(
//                                new String[]{"first row button1", "first row button2"},
//                                new String[]{"second row button1", "second row button2"})
//                                .oneTimeKeyboard(true)   // optional
//                                .resizeKeyboard(true)    // optional
//                                .selective(true);        // optional
//
//                        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup(
//                                new InlineKeyboardButton[]{
//                                        new InlineKeyboardButton("url").url("www.google.com"),
//                                        new InlineKeyboardButton("callback_data").callbackData("callback_data"),
//                                        new InlineKeyboardButton("switch_inline_query").switchInlineQuery("switch_inline_query")
//                                });

                        bot.execute(new SendMessage(userId, "echo: " + text + "\nmy time is: " + new Date())
//                                .replyMarkup(replyKeyboardMarkup)
//                                .replyMarkup(inlineKeyboard)
//                                .replyMarkup( new ReplyKeyboardMarkup(new String[]{"test1"}, new String[]{"test2"}, new String[]{"test3"}))
//                                .replyMarkup(new ReplyKeyboardMarkup(new String[]{"a","b","c"}))
                        );

//        SendMessage request = new SendMessage(chatId, "text")
//                .parseMode(ParseMode.HTML)
//                .disableWebPagePreview(true)
//                .disableNotification(true)
//                .replyToMessageId(1)
//                .replyMarkup(new ForceReply());

                    }
                }
                return UpdatesListener.CONFIRMED_UPDATES_ALL; // all updates processed
            }
        });
    }
}
