/*
 * Copyright 2019 ProtectionStones team and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package dev.espi.protectionstones.commands;

import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.FlagHandler;
import dev.espi.protectionstones.PSL;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.ProtectionStones;
import dev.espi.protectionstones.utils.WGMerge;
import dev.espi.protectionstones.utils.WGUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.*;

public class ArgAdminForceMerge {

    private static Map<Flag<?>, Object> getFlags(Map<Flag<?>, Object> flags) {
        Map<Flag<?>, Object> f = new HashMap<>(flags);
        f.remove(FlagHandler.PS_BLOCK_MATERIAL);
        f.remove(FlagHandler.PS_MERGED_REGIONS_TYPES);
        f.remove(FlagHandler.PS_MERGED_REGIONS);
        f.remove(FlagHandler.PS_NAME);
        f.remove(FlagHandler.PS_HOME);

        return f;
    }

    private static boolean areDomainsEqual(DefaultDomain o1, DefaultDomain o2) {
        for (UUID uuid : o1.getUniqueIds()) {
            if (!o2.contains(uuid)) return false;
        }
        for (UUID uuid : o2.getUniqueIds()) {
            if (!o1.contains(uuid)) return true;
        }
        return true;
    }

    // /ps admin forcemerge [world]
    public static boolean argumentAdminForceMerge(CommandSender p, String[] args) {
        if (args.length < 3) {
            PSL.msg(p, PSL.ADMIN_FORCEMERGE_HELP.msg());
            return true;
        }

        String world = args[2];
        World w = Bukkit.getWorld(world);

        if (w == null) {
            PSL.msg(p, PSL.INVALID_WORLD.msg());
            return true;
        }

        RegionManager rm = WGUtils.getRegionManagerWithWorld(Bukkit.getWorld(world));

        HashMap<String, String> idToGroup = new HashMap<>();
        HashMap<String, List<PSRegion>> groupToMembers = new HashMap<>();

        for (ProtectedRegion r : rm.getRegions().values()) {
            if (!ProtectionStones.isPSRegion(r)) continue;
            if (r.getParent() != null) continue;
            boolean merged = idToGroup.get(r.getId()) != null;

            Bukkit.getLogger().info("NEW"); //TODO

            Map<Flag<?>, Object> baseFlags = getFlags(r.getFlags()); // comparison flags

            PSRegion psr = PSRegion.fromWGRegion(w, r);

            for (ProtectedRegion rOverlap : rm.getApplicableRegions(r)) {
                if (!ProtectionStones.isPSRegion(rOverlap)) continue;
                if (rOverlap.getId().equals(r.getId())) continue;

                Map<Flag<?>, Object> mergeFlags = getFlags(rOverlap.getFlags()); // comparison flags

                if (!(areDomainsEqual(rOverlap.getOwners(), r.getOwners()) && areDomainsEqual(rOverlap.getMembers(), r.getMembers()) && rOverlap.getParent() == null && baseFlags.equals(mergeFlags))) continue;

                String rOverlapGroup = idToGroup.get(rOverlap.getId());
                if (merged) { // r is part of a group
                    String rGroup = idToGroup.get(r.getId());
                    Bukkit.getLogger().info("" + (groupToMembers.get(rGroup) == null)); // TODO
                    if (rOverlapGroup == null) { // rOverlap not part of a group
                        idToGroup.put(rOverlap.getId(), rGroup);
                        groupToMembers.get(rGroup).add(PSRegion.fromWGRegion(w, rOverlap));
                    } else if (!rOverlapGroup.equals(rGroup)) { // rOverlap is part of a group (both are part of group)

                        Bukkit.getLogger().info("MHM"); //TODO
                        for (PSRegion pr : groupToMembers.get(rOverlapGroup)) {
                            idToGroup.put(pr.getID(), rGroup);
                        }
                        groupToMembers.get(rGroup).addAll(groupToMembers.get(rOverlapGroup));
                        groupToMembers.remove(rOverlapGroup);
                    }
                } else { // r not part of group
                    if (rOverlapGroup == null) { // both are not part of group
                        idToGroup.put(r.getId(), r.getId());
                        idToGroup.put(rOverlap.getId(), r.getId());
                        groupToMembers.put(r.getId(), new ArrayList<>(Arrays.asList(psr, PSRegion.fromWGRegion(w, rOverlap))));
                    } else { // rOverlap is part of group
                        idToGroup.put(r.getId(), rOverlapGroup);
                        groupToMembers.get(rOverlapGroup).add(psr);
                    }
                    merged = true;
                }

            }
        }

        for (String key : groupToMembers.keySet()) {
            PSRegion root = null;
            p.sendMessage(ChatColor.GRAY + "Merging these regions into " + key + ":");
            for (PSRegion r : groupToMembers.get(key)) {
                if (r.getID().equals(key)) root = r;
                p.sendMessage(ChatColor.GRAY + r.getID());
            }
            try {
                WGMerge.mergeRegions(w, rm, root, groupToMembers.get(key));
            } catch (WGMerge.RegionHoleException e) {
                // TODO
            }
        }

        p.sendMessage(ChatColor.GRAY + "Done!");

        return true;
    }
}