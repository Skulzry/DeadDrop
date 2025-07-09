package com.skulzry.stuff;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.util.Map;

public class DeadDrop extends JavaPlugin implements Listener {

    private JDA jda;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String token = getConfig().getString("token");

        if (token == null || token.isEmpty()) {
            getLogger().severe("Discord token is missing from config.yml!");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MEMBERS
                    )
                    .addEventListeners(new DiscordListener())
                    .build();

            String guildId = getConfig().getString("guild-id"); // Add this to config.yml

            jda.awaitReady();

            if (guildId != null && !guildId.isEmpty()) {
                jda.getGuildById(guildId).updateCommands().addCommands(
                    Commands.slash("heartcount", "Shows heart count(s)")
                            .addOption(OptionType.STRING, "player", "Player name", false),
                    Commands.slash("armor", "Shows armor of player(s)")
                            .addOption(OptionType.STRING, "player", "Player name", false)
                ).queue();
                getLogger().info("Registered commands instantly to guild " + guildId);
            } else {
                getLogger().warning("No guild_id set in config.yml. Slash commands will register globally with delay.");
            }

            getLogger().info("Discord bot is ready!");

        } catch (LoginException | InterruptedException e) {
            getLogger().severe("Failed to start JDA: " + e.getMessage());
            e.printStackTrace();
        }

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("heartcount")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by a player.");
                return true;
            }

            if (!sender.hasPermission("stuff.heartcount")) {
                sender.sendMessage("You don't have permission to use this command.");
                return true;
            }

            sender.sendMessage("§cHeart Counts:§f");
            for (Player p : getServer().getOnlinePlayers()) {
                double maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                int hearts = (int) Math.round(maxHealth / 2.0);
                sender.sendMessage(p.getName() + ": " + hearts);
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("armor")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("This command can only be used by a player.");
                return true;
            }

            if (!sender.hasPermission("stuff.armor")) {
                sender.sendMessage("You don't have permission to use this command.");
                return true;
            }

            if (args.length > 0) {
                Player player = Bukkit.getPlayerExact(args[0]);
                if (player != null && player.isOnline()) {
                    sender.sendMessage("§c" + player.getName() + ":§f\n" + getArmorString(player));
                } else {
                    sender.sendMessage("Player not found.");
                }
            } else {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    sender.sendMessage("§c" + p.getName() + ":§f\n" + getArmorString(p));
                }
            }

            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        String deathMessage = event.getDeathMessage();
        if (deathMessage == null) return;

        String victim = event.getEntity().getName();
        String killer = event.getEntity().getKiller() != null ? event.getEntity().getKiller().getName() : null;

        deathMessage = deathMessage.replace(victim, "**" + victim + "**");
        if (killer != null) {
            deathMessage = deathMessage.replace(killer, "**" + killer + "**");
        }

        if (jda != null) {
            for (TextChannel channel : jda.getTextChannelsByName("system", true)) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("Player Death");
                embed.setDescription(deathMessage);
                embed.setColor(Color.RED);
                channel.sendMessageEmbeds(embed.build()).queue();
                break;
            }
        }
    }

    private String getArmorString(Player p) {
        StringBuilder sb = new StringBuilder();
        ItemStack[] armor = p.getInventory().getArmorContents();

        sb.append("Helmet: ").append(describeItem(armor[3])).append("\n");
        sb.append("Chestplate: ").append(describeItem(armor[2])).append("\n");
        sb.append("Leggings: ").append(describeItem(armor[1])).append("\n");
        sb.append("Boots: ").append(describeItem(armor[0])).append("\n");

        return sb.toString().trim();
    }

    private String describeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "NONE";

        StringBuilder desc = new StringBuilder();
        String name = item.getType().toString().replace("_", " ").toLowerCase();
        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        desc.append(name);

        if (item.getItemMeta() instanceof Damageable) {
            Damageable dmg = (Damageable) item.getItemMeta();
            int maxDurability = item.getType().getMaxDurability();
            int durabilityLeft = maxDurability - dmg.getDamage();
            desc.append(" (").append(durabilityLeft).append("/").append(maxDurability).append(" durability)");
        }

        Map<Enchantment, Integer> enchants = item.getEnchantments();
        if (!enchants.isEmpty()) {
            desc.append(" [");
            boolean first = true;
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                if (!first) desc.append(", ");
                String enchName = entry.getKey().getKey().getKey().replace("_", " ");
                enchName = Character.toUpperCase(enchName.charAt(0)) + enchName.substring(1);
                desc.append(enchName).append(" ").append(entry.getValue());
                first = false;
            }
            desc.append("]");
        }

        return desc.toString();
    }

    private String getArmorInfo(String playerName) {
        StringBuilder sb = new StringBuilder();
        if (playerName != null) {
            Player p = Bukkit.getPlayerExact(playerName);
            if (p != null && p.isOnline()) {
                sb.append("**").append(p.getName()).append("**\n");
                sb.append(getArmorString(p)).append("\n");
            } else {
                sb.append("Player ").append(playerName).append(" not found or offline.");
            }
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                sb.append("**").append(p.getName()).append("**\n");
                sb.append(getArmorString(p)).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private String getHeartCounts() {
        StringBuilder sb = new StringBuilder();
        for (Player p : Bukkit.getOnlinePlayers()) {
            double maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            int hearts = (int) Math.round(maxHealth / 2.0);
            sb.append("**").append(p.getName()).append("**: ").append(hearts).append("\n");
        }
        return sb.toString().trim();
    }

    public class DiscordListener extends ListenerAdapter {
        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            String name = event.getName();

            if (name.equals("heartcount")) {
                OptionMapping playerOption = event.getOption("player");
                String playerName = playerOption != null ? playerOption.getAsString() : null;

                EmbedBuilder embed = new EmbedBuilder().setColor(Color.RED);

                if (playerOption != null) {
                    Player p = Bukkit.getPlayerExact(playerOption.getAsString());
                    if (p != null && p.isOnline()) {
                        double maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                        int hearts = (int) Math.round(maxHealth / 2.0);
                        embed.setTitle("Heart Counts");
                        embed.setDescription(p.getName() + ": " + hearts);
                    } else {
                        embed.setTitle("Player not found");
                        embed.setDescription("Player '" + playerName + "' not found or offline.");
                    }
                } else if (Bukkit.getOnlinePlayers().isEmpty()) {
                    List<String> descriptions = List.of(
                        "At least the server's on...",
                        "Maybe you should join...",
                        "Hop on!",
                        "Ping some people to hop on.",
                        "Yeah... I'ma turn off the server soon.",
                        "o7",
                        "Do people even read these?"
                    );

                    embed.setTitle("No players online!");
                    embed.setDescription(descriptions.get(new Random().nextInt(descriptions.size())));
                } else {
                    embed.setTitle("Heart Counts");
                    embed.setDescription(getHeartCounts());
                }

                event.replyEmbeds(embed.build()).queue();
            }

            if (name.equals("armor")) {
                OptionMapping playerOption = event.getOption("player");
                String playerName = playerOption != null ? playerOption.getAsString() : null;

                EmbedBuilder embed = new EmbedBuilder().setColor(Color.RED);

                if (playerName != null) {
                    Player p = Bukkit.getPlayerExact(playerName);
                    if (p != null && p.isOnline()) {
                        embed.setTitle(p.getName());
                        embed.setDescription(getArmorString(p));
                    } else {
                        embed.setTitle("Player not found");
                        embed.setDescription("Player '" + playerName + "' not found or offline.");
                    }
                } else if (Bukkit.getOnlinePlayers().isEmpty()) {
                    List<String> descriptions = List.of(
                        "At least the server's on...",
                        "Maybe you should join...",
                        "Hop on!",
                        "Ping some people to hop on.",
                        "Yeah... I'ma turn off the server soon.",
                        "o7",
                        "Do people even read these?"
                    );

                    embed.setTitle("No players online!");
                    embed.setDescription(descriptions.get(new Random().nextInt(descriptions.size())));
                } else {
                    embed.setTitle("All");
                    embed.setDescription(getArmorInfo(null));
                }

                event.replyEmbeds(embed.build()).queue();
            }
        }
    }
}