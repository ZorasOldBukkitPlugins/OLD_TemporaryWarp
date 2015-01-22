package com.mrz.dyndns.server.TemporaryWarp;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class TemporaryWarp extends JavaPlugin implements Listener
{
	public static TemporaryWarp plugin;
	public static final Logger log = Logger.getLogger("Minecraft");
	List<String> warpList = new ArrayList<String>();
	public static Economy econ = null;
	MyConfig warps = new MyConfig(this, "locations");
	//HashMap<String, String> toBeTeleportedBack = new HashMap<String, String>();
	//Map<String, String> toBeBack = new Map<String, String>();
	
	//todo. handle player deaths
	
	@Override
	public void onEnable() 
	{
		
		if (!setupEconomy() ) {
            log.log(Level.SEVERE, String.format("[%s] - Disabled due to no Vault dependency found!", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
		
		warps.getCustomConfig().options().copyDefaults(true);
		warpList = getWarpList();
		
		getServer().getPluginManager().registerEvents(this, this);
		
		if(warps.getCustomConfig().getString("defaultLocation.world") == null)
		{
			log.info("[TWarp] Setting default Location to " + Bukkit.getWorlds().get(0).getName() + "'s spawn.");
			setDefaultWarp(Bukkit.getWorlds().get(0).getSpawnLocation());
		}
		
	}
	
	@Override
	public void onDisable() 
	{
		warps.saveCustomConfig();
		saveList(warpList);
	}
	
	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event)
	{
		for (String warp : warpList)
		{
			if(warps.getCustomConfig().getBoolean(warp + ".pendingPlayers." + event.getPlayer().getName()) == true)
			{
				event.getPlayer().teleport(getReturnLocation(warp));
				warps.getCustomConfig().set(warp + ".pendingPlayers." + event.getPlayer().getName(), false);
				warps.saveCustomConfig();
			}
		}
	}
	
	private boolean setupEconomy()
	{
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
	}
	
	public boolean transact (Player player, String name)
	{
		double warpCost = warps.getCustomConfig().getDouble(name + ".cost");
		
		double amountOwned = econ.getBalance(player.getName());
		
		if(warpCost == 0)
		{
			return true;
		}
		
		else if(warpCost > amountOwned)
		{
			player.sendMessage(ChatColor.RED + "Insufficient funds!");
			return false;
		}
		
		else
		{
			EconomyResponse r = econ.withdrawPlayer(player.getName(), warpCost);
			if(r.transactionSuccess())
			{
				player.sendMessage(ChatColor.GREEN + "" + warpCost + "" + ChatColor.DARK_GREEN + " has been withdrawn from your account.");
				return true;
			}
			else
			{
				player.sendMessage(String.format("An error has occured: %s", r.errorMessage));
				return false;
			}
		}
	}
	
	public void delayTeleportBack(final String playerName,final String warpName, final long ticks)
	{
		getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() 
		{
			public void run() 
			{

				
				if(Bukkit.getPlayer(playerName) != null)
				{

					if(warps.getCustomConfig().getBoolean(warpName + ".usingDefaultReturn"))
					{
						Bukkit.getPlayer(playerName).teleport(getDefaultWarp());
					}
					else
					{
						Bukkit.getPlayer(playerName).teleport(getReturnLocation(warpName));
					}
					//toBeTeleportedBack.remove(playerName);
					warps.getCustomConfig().set(warpName + ".pendingPlayers." + playerName, null);
					//System.out.println("[DEBUG] " + playerName + " has been removed from the list.");
				}
				else
				{
					//System.out.println("[DEBUG] " + playerName + " is not on his server and his time is up. He will be teleported back when he logs in.");
				}
				//player.teleport(previousLocations.get(player.getName())); 
				//previousLocations.remove(player.getName());
			} 
		}, ticks);
		
		getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() 
		{
			public void run() 
			{
				if(Bukkit.getPlayer(playerName) != null)
				{
					Bukkit.getPlayer(playerName).sendMessage(ChatColor.DARK_RED + "10 seconds remaining!");
				}
				//player.teleport(previousLocations.get(player.getName())); 
				//previousLocations.remove(player.getName());
			} 
		}, ticks - 200);
	}
	
	public boolean teleportPlayer(Player player, String name, boolean free)
	{
		if(warpExists(name))
		{
			if(transact(player, name) || free == true)
			{
				Location loc = new Location(null,0,0,0,0,0);
				loc.setWorld(Bukkit.getWorld(warps.getCustomConfig().getString(name + ".world")));
				loc.setX(warps.getCustomConfig().getDouble(name + ".X"));
				loc.setY(warps.getCustomConfig().getDouble(name + ".Y"));
				loc.setZ(warps.getCustomConfig().getDouble(name + ".Z"));
				loc.setYaw((float) warps.getCustomConfig().getDouble(name + ".Yaw"));
				loc.setPitch((float) warps.getCustomConfig().getDouble(name + ".Pitch"));
				
				//previousLocations.put(player.getName(), player.getLocation());
				
				player.teleport(loc);
				//set timer
				//toBeTeleportedBack.put(player.getName(), name);
				warps.getCustomConfig().set(name + ".pendingPlayers." + player.getName(), true);
				//System.out.println("[DEBUG] " + player.getName() + " has been added to the list.");
				delayTeleportBack(player.getName(),name, warps.getCustomConfig().getLong(name + ".time") * 20);
				warps.saveCustomConfig();
				return true;
			}
			else
			{
				return false;
			}
		}
		else
		{
			player.sendMessage(ChatColor.RED + "That warp doesn't exist!");
			return false;
		}
	}
	
	public void addWarpToList(String warpName)
	{
		warpList.add(warpName);
	}
	
	public void removeWarpFromList(String warpName)
	{
		warpList.remove(warpName);
	}
	
	public void saveList(List<String> warps)
	{
		sortList(warps);
		String[] warpList = warps.toArray(new String[warps.size()]);
		this.warps.getCustomConfig().set("warps", warpList);
	}
	
	public List<String> getWarpList()
	{
		List<String> warps = this.warps.getCustomConfig().getStringList("warps");
		return warps;
	}
	
	public void deleteWarp(String name)
	{
		warps.getCustomConfig().set(name + ".world", null);
		warps.getCustomConfig().set(name + ".X", null);
		warps.getCustomConfig().set(name + ".Y", null);
		warps.getCustomConfig().set(name + ".Z", null);
		warps.getCustomConfig().set(name + ".Yaw", null);
		warps.getCustomConfig().set(name + ".Pitch", null);
		warps.getCustomConfig().set(name + ".cost", null);
		warps.getCustomConfig().set(name + ".time", null);
		
		warps.getCustomConfig().set(name + ".returnLocation.world", null);
		warps.getCustomConfig().set(name + ".returnLocation.X", null);
		warps.getCustomConfig().set(name + ".returnLocation.Y", null);
		warps.getCustomConfig().set(name + ".returnLocation.Z", null);
		warps.getCustomConfig().set(name + ".returnLocation.Yaw", null);
		warps.getCustomConfig().set(name + ".returnLocation.Pitch", null);
		warps.getCustomConfig().set(name + ".usingDefaultReturn", null);
		
		removeWarpFromList(name);
		
		saveList(warpList);
		warps.saveCustomConfig();
	}
	
	public void createWarp(String name, Location loc, long seconds)
	{
		warps.getCustomConfig().set(name + ".world", loc.getWorld().getName());
		warps.getCustomConfig().set(name + ".X", loc.getX());
		warps.getCustomConfig().set(name + ".Y", loc.getY());
		warps.getCustomConfig().set(name + ".Z", loc.getZ());
		warps.getCustomConfig().set(name + ".Yaw", loc.getYaw());
		warps.getCustomConfig().set(name + ".Pitch", loc.getPitch());
		warps.getCustomConfig().set(name + ".cost", 0);
		warps.getCustomConfig().set(name + ".time", seconds);
		
		/*
		warps.getCustomConfig().set(name + ".returnLocation.world", warps.getCustomConfig().getString("defaultLocation.world"));
		warps.getCustomConfig().set(name + ".returnLocation.X", warps.getCustomConfig().getDouble("defaultLocation.X"));
		warps.getCustomConfig().set(name + ".returnLocation.Y", warps.getCustomConfig().getDouble("defaultLocation.Y"));
		warps.getCustomConfig().set(name + ".returnLocation.Z", warps.getCustomConfig().getDouble("defaultLocation.Z"));
		warps.getCustomConfig().set(name + ".returnLocation.Yaw", warps.getCustomConfig().getDouble("defaultLocation.Yaw"));
		warps.getCustomConfig().set(name + ".returnLocation.Pitch", warps.getCustomConfig().getDouble("defaultLocation.Pitch"));
		*/
		warps.getCustomConfig().set(name + ".usingDefaultReturn", true);
		
		addWarpToList(name);
		saveList(warpList);
		warps.saveCustomConfig();
	}
	
	public void setDefaultWarp(Location loc)
	{
		warps.getCustomConfig().set("defaultLocation.world", loc.getWorld().getName());
		warps.getCustomConfig().set("defaultLocation.X", loc.getX());
		warps.getCustomConfig().set("defaultLocation.Y", loc.getY());
		warps.getCustomConfig().set("defaultLocation.Z", loc.getZ());
		warps.getCustomConfig().set("defaultLocation.Yaw", loc.getYaw());
		warps.getCustomConfig().set("defaultLocation.Pitch", loc.getPitch());
	}
	
	public Location getDefaultWarp()
	{
		Location loc = new Location(null,0,0,0,0,0);
		loc.setWorld(Bukkit.getWorld(warps.getCustomConfig().getString("defaultLocation.world")));
		loc.setX(warps.getCustomConfig().getDouble("defaultLocation.X"));
		loc.setY(warps.getCustomConfig().getDouble("defaultLocation.Y"));
		loc.setZ(warps.getCustomConfig().getDouble("defaultLocation.Z"));
		loc.setYaw((float) warps.getCustomConfig().getDouble("defaultLocation.Yaw"));
		loc.setPitch((float) warps.getCustomConfig().getDouble("defaultLocation.Pitch"));
		return loc;
	}
	
	public Location getReturnLocation(String warpName)
	{
		Location loc = new Location(null,0,0,0,0,0);
		loc.setWorld(Bukkit.getWorld(warps.getCustomConfig().getString(warpName + ".returnLocation.world")));
		loc.setX(warps.getCustomConfig().getDouble(warpName + ".returnLocation.X"));
		loc.setY(warps.getCustomConfig().getDouble(warpName + ".returnLocation.Y"));
		loc.setZ(warps.getCustomConfig().getDouble(warpName + ".returnLocation.Z"));
		loc.setYaw((float) warps.getCustomConfig().getDouble(warpName + ".returnLocation.Yaw"));
		loc.setPitch((float) warps.getCustomConfig().getDouble(warpName + ".returnLocation.Pitch"));
		return loc;
	}
	
	public void setReturnLocation(String warpName, Location loc)
	{
		warps.getCustomConfig().set(warpName + ".returnLocation.world", loc.getWorld().getName());
		warps.getCustomConfig().set(warpName + ".returnLocation.X", loc.getX());
		warps.getCustomConfig().set(warpName + ".returnLocation.Y", loc.getY());
		warps.getCustomConfig().set(warpName + ".returnLocation.Z", loc.getZ());
		warps.getCustomConfig().set(warpName + ".returnLocation.Yaw", loc.getYaw());
		warps.getCustomConfig().set(warpName + ".returnLocation.Pitch", loc.getPitch());
		
		warps.getCustomConfig().set(warpName + ".usingDefaultReturn", false);
		
		warps.saveCustomConfig();
	}
	
	public boolean setWarpCost(String warpName, double cost)
	{
		if(!warpExists(warpName))
		{
			return false;
		}
		else
		{
			warps.getCustomConfig().set(warpName + ".cost", cost);
			warps.saveCustomConfig();
			return true;
		}
	}
	
	public double getWarpCost(String warpName)
	{
		if(warpExists(warpName))
		{
			return (warps.getCustomConfig().getDouble(warpName + ".cost"));
		}
		else
		{
			return (Double) null;
		}
	}
	
	public boolean setWarpTime(String warpName, long seconds)
	{
		if(!warpExists(warpName))
		{
			return false;
		}
		else
		{
			warps.getCustomConfig().set(warpName + ".time", seconds);
			warps.saveCustomConfig();
			return true;
		}
	}
	
	public long getWarpTime(String warpName)
	{
		if(warpExists(warpName))
		{
			return (warps.getCustomConfig().getLong(warpName + ".time"));
		}
		else
		{
			return (Long) null;
		}
	}
	
	public void sortList(List<String> list)
	{
		java.util.Collections.sort(list);
	}
	
	public void printList(Player player, String[] args)
	{
		int listSize = 9;
		double pagesRaw = 1.0;
		//Set<OfflinePlayer> players = Bukkit.getWhitelistedPlayers();
		//OfflinePlayer[] arrayPlayers = players.toArray(new OfflinePlayer[0]);
		sortList(warpList);
		String[] warpArray = warpList.toArray(new String[warpList.size()]);
		int length = warpList.size();
		pagesRaw = length / listSize;
		int pagesRawRemaider = length % listSize;
		int pages = (int) pagesRaw;
		pages++;
		int currentPage;
		if(args.length == 1)
		{
			currentPage = 1;
		}
		else
		{
			currentPage = Integer.parseInt(args[1]);
		}
		
		int numberToDisplay = 9;
		if (currentPage > pagesRaw)
		{
			numberToDisplay = pagesRawRemaider;
		}
		
		//int numberOfSetsOfNine = length / 
		player.sendMessage(ChatColor.YELLOW + "------------------" + ChatColor.GREEN + "Warps" + ChatColor.YELLOW + " - " 
				+ ChatColor.LIGHT_PURPLE + " page " + currentPage + ChatColor.DARK_PURPLE + " of " + ChatColor.LIGHT_PURPLE + pages + ChatColor.YELLOW + "-----------------");
		for(int ii = currentPage * listSize; ii < (currentPage * listSize ) + numberToDisplay; ii++)
		{
			//try 
			//{
				player.sendMessage(ChatColor.AQUA + warpArray[ii-listSize]);
			//} 
			//catch (ArrayIndexOutOfBoundsException e)
			//{
			//	return true;
			//}
		}
	}
	
	public boolean warpExists(String name)
	{
		if(warps.getCustomConfig().getString(name + ".world") == null)
		{
			return false;
		}
		else
		{
			return true;
		}
	}
	
	public void setWarpLocation(String name, Location loc)
	{
		warps.getCustomConfig().set(name + ".world", loc.getWorld().getName());
		warps.getCustomConfig().set(name + ".X", loc.getX());
		warps.getCustomConfig().set(name + ".Y", loc.getY());
		warps.getCustomConfig().set(name + ".Z", loc.getZ());
		warps.getCustomConfig().set(name + ".Yaw", loc.getYaw());
		warps.getCustomConfig().set(name + ".Pitch", loc.getPitch());
		
		warps.saveCustomConfig();
	}
	
	public String noPermissions()
	{
		return ChatColor.RED + "You don't have permission to do that!";
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) 
	{
		int length = args.length;
		
		Player player = null;
		if (sender instanceof Player)
		{
			player = (Player) sender;
		}
		
		if(length == 0)
		{
			sender.sendMessage(ChatColor.RED + "Not enough arguments!");
			return false;
		}
		
		if(args[0].equalsIgnoreCase("list"))
		{
			if(sender.hasPermission("twarp.list"))
			{
				//sender.sendMessage("You view the warp list");
				printList(player, args);
				return true;
			}
			else
			{
				sender.sendMessage(noPermissions());
				return true;
			}
		}
		
		if (length == 1)
		{
			if(args[0].equalsIgnoreCase("reload"))
			{
				if(sender.hasPermission("twarp.reload"))
				{
					//sender.sendMessage("you reload the configuration");
					warps.reloadCustomConfig();
					sender.sendMessage(ChatColor.GREEN + "Locations.yml has been reloaded.");
					return true;
				}
				else
				{
					sender.sendMessage(noPermissions());
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("default"))
			{
				if(player == null)
				{
					sender.sendMessage(ChatColor.RED + "You must be a player to use this command!");
					return true;
				}
				if(sender.hasPermission("twarp.default"))
				{
					//sender.sendMessage("you set the default warp return location.");
					setDefaultWarp(player.getLocation());
					sender.sendMessage(ChatColor.GREEN + "Default location set.");
					return true;
				}
				else
				{
					sender.sendMessage(noPermissions());
					return true;
				}
			}
			else
			{
				if(player == null)
				{
					sender.sendMessage(ChatColor.RED + "You must be a player to use this command!");
					return true;
				}
				if(sender.hasPermission("twarp.use"))
				{
					//sender.sendMessage("You use a warp");
					if(teleportPlayer(player, args[0], false))
					{
						if(getWarpCost(args[0]) != 0.0)
						{
							sender.sendMessage(ChatColor.GREEN + "You have been warped to " + ChatColor.YELLOW + args[0] + 
									ChatColor.GREEN + " for " + ChatColor.YELLOW + getWarpCost(args[0]) + ChatColor.GREEN + "!");
						}
						else
						{
							sender.sendMessage(ChatColor.GREEN + "You have been warped to " + ChatColor.YELLOW + args[0] + ChatColor.GREEN + "!");
						}
					}
					return true;
				}
				else
				{
					sender.sendMessage(noPermissions());
					return true;
				}
			}
		}
		
		if (length == 2)
		{
			if(args[0].equalsIgnoreCase("clear"))
			{
				if(sender.hasPermission("twarp.clear"))
				{
					//sender.sendMessage("You clear a warp.");
					if(warpExists(args[1]))
					{
						deleteWarp(args[1]);
						sender.sendMessage(ChatColor.GREEN + args[1] + " has been deleted.");
					}
					else
					{
						sender.sendMessage(ChatColor.RED + "Warp " + args[1] + " doesn't exist!");
					}
					return true;
				}
				else
				{
					sender.sendMessage(noPermissions());
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("loc"))
			{
				if(player == null)
				{
					sender.sendMessage(ChatColor.RED + "You must be a player to use this command!");
					return true;
				}
				if(sender.hasPermission("twarp.loc"))
				{
					if(warpExists(args[1]))
					{
						setWarpLocation(args[1], player.getLocation());
						sender.sendMessage(ChatColor.GREEN + "New warp location for warp " + args[1] + " has been set.");
					}
				}
				else
				{
					sender.sendMessage(noPermissions());
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("return"))
			{
				if(player == null)
				{
					sender.sendMessage(ChatColor.RED + "You must be a player to use this command!");
					return true;
				}
				if(sender.hasPermission("twarp.return"))
				{
					//sender.sendMessage("You set the return location for a warp.");
					setReturnLocation(args[1], player.getLocation());
					sender.sendMessage(ChatColor.GREEN + "The return location for " + args[1] + " has been set.");
					return true;
				}
				else
				{
					sender.sendMessage(noPermissions());
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("info"))
			{
				if(sender.hasPermission("twarp.info"))
				{
					//sender.sendMessage("You view information about a warp.");
					if(warpExists(args[1]))
					{
						sender.sendMessage(ChatColor.YELLOW + "Info about warp: " + args[1]);
						sender.sendMessage(ChatColor.GOLD + "Cost: " + getWarpCost(args[1]));
						sender.sendMessage(ChatColor.GOLD + "Seconds: " + getWarpTime(args[1]));
					}
					else
					{
						sender.sendMessage(ChatColor.RED + "That warp doesn't exist!");
					}
					return true;
				}
				else
				{
					sender.sendMessage(noPermissions());
					return true;
				}
			}
			else
			{
				sender.sendMessage(ChatColor.RED + "Too many arguments!");
				return false;
			}
		}
		
		if(args[0].equalsIgnoreCase("set"))
		{
			if(sender.hasPermission("twarp.set"))
			{
				if(player == null)
				{
					sender.sendMessage(ChatColor.RED + "You must be a player to use this command!");
					return true;
				}
				if(length < 3)
				{
					sender.sendMessage(ChatColor.RED + "Invalid usage!");
					return false;
				}
				else if (length == 3)
				{//public void createWarp(String name, Location loc, long seconds)
					//sender.sendMessage("You set a warp without the price");
					
					try{Long.parseLong(args[2]);} catch (NumberFormatException e) {
						sender.sendMessage(ChatColor.RED + "Invalid input!");
						return false;
					}
					
					if(Long.parseLong(args[2]) < 10)
					{
						sender.sendMessage(ChatColor.RED + "Time length cannot be less than ten!");
						return true;
					}
					
					if(warpExists(args[1]))
					{
						sender.sendMessage(ChatColor.RED + "There is already a warp named " + args[1] + "!");
						return true;
					}
					
					createWarp(args[1], player.getLocation(), Long.parseLong(args[2]));
					sender.sendMessage(ChatColor.GREEN + "Warp " + args[1] + " has been set.");
					return true;
				}
				else if (length == 4)
				{
					if(sender.hasPermission("twarp.cost"))
					{
						try{Long.parseLong(args[2]);} catch (NumberFormatException e) {
							sender.sendMessage(ChatColor.RED + "Invalid input!");
							return false;
						}
						
						try{Double.parseDouble(args[3]);} catch (NumberFormatException e) {
							sender.sendMessage(ChatColor.RED + "Invalid input!");
							return false;
						}
						
						//sender.sendMessage("You set a warp with the price");
						createWarp(args[1], player.getLocation(), Long.parseLong(args[2]));
						setWarpCost(args[1], Double.parseDouble(args[3]));
						sender.sendMessage(ChatColor.GREEN + "Warp " + args[1] + " has been set with cost " + args[3] + ".");
						return true;
					}
					else
					{
						sender.sendMessage(ChatColor.RED + "You don't have permission to set the price!");
						return true;
					}
				}
				else
				{
					sender.sendMessage(ChatColor.RED + "Too many arguments!");
					return false;
				}
			}
			else
			{
				sender.sendMessage(noPermissions());
				return true;
			}
		}
		
		if(length == 3)
		{
			if(args[0].equalsIgnoreCase("time"))
			{
				if(sender.hasPermission("twarp.time"))
				{
					//sender.sendMessage("You set the return time for a warp.");
					
					if(warpExists(args[1]))
					{
						try{Long.parseLong(args[2]);} catch (NumberFormatException e) {
							sender.sendMessage(ChatColor.RED + "Invalid input!");
							return false;
						}
						
						if(Long.parseLong(args[2]) < 10)
						{
							sender.sendMessage(ChatColor.RED + "Time length cannot be less than ten!");
							return true;
						}
						
						setWarpTime(args[1], Long.parseLong(args[2]));
						sender.sendMessage(ChatColor.GREEN + "Warp time for warp " + args[1] + " has been set to " + args[2]);
					}
					else
					{
						sender.sendMessage(ChatColor.RED + "That warp doesn't exist!");
					}
					return true;
				}
				else
				{
					sender.sendMessage(noPermissions());
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("cost") || args[0].equalsIgnoreCase("price"))
			{
				if(sender.hasPermission("twarp.cost"))
				{
					//sender.sendMessage("You set the cost for the warp.");
					if(warpExists(args[1]))
					{
						try{Double.parseDouble(args[2]);} catch (NumberFormatException e) {
							sender.sendMessage(ChatColor.RED + "Invalid input!");
							return false;
						}
						setWarpCost(args[1], Double.parseDouble(args[2]));
						sender.sendMessage(ChatColor.GREEN + "Warp cost for warp " + args[1] + " has been set to " + args[2]);
					}
					else
					{
						sender.sendMessage(ChatColor.RED + "That warp doesn't exist!");
					}
					return true;
				}
				else
				{
					sender.sendMessage(noPermissions());
					return true;
				}
			}
			else if (args[0].equalsIgnoreCase("other"))
			{
				if(sender.hasPermission("twarp.other"))
				{
					//sender.sendMessage("You warp another player to a warp.");
					//System.out.println("checkoint");
					Player target = Bukkit.getPlayer(args[1]);
					if(target == null)
					{
						sender.sendMessage(ChatColor.RED + "There is no player named " + args[1] + "!");
						return true;
					}
					else if (!warpExists(args[2]))
					{
						sender.sendMessage(ChatColor.RED + "There is no warp named " + args[0] + "!");
						return true;
					}
					else
					{
						//System.out.println(args[0] + "," + args[1] + "," + args[2]);
						sender.sendMessage(ChatColor.GREEN + args[1] + " has been warped to " + args[2]);
						return(teleportPlayer(target, args[2], true));
					}
				}
				else
				{
					sender.sendMessage(noPermissions());
					return true;
				}
			}
			else
			{
				sender.sendMessage(ChatColor.RED + "Too many arguments!");
				return false;
			}
		}
		return false;
	}
}
