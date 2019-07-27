/*
 * Copyright 2019 ProtectionStones team and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.espi.protectionstones;

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.event.PSRemoveEvent;
import dev.espi.protectionstones.utils.WGUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents an instance of a protectionstones protected region.
 */

public class PSRegion {
    private ProtectedRegion wgregion;
    private RegionManager rgmanager;
    private World world;

    PSRegion(ProtectedRegion wgregion, RegionManager rgmanager, World world) {
        this.wgregion = checkNotNull(wgregion);
        this.rgmanager = checkNotNull(rgmanager);
        this.world = checkNotNull(world);
    }

    // ~~~~~~~~~~~~~~~~~ static ~~~~~~~~~~~~~~~~~

    /**
     * Get the protection stone region that the location is in, or the closest one if there are overlapping regions.
     *
     * @param l the location
     * @return the {@link PSRegion} object if the location is in a region, or null if the location is not in a region
     */
    public static PSRegion fromLocation(Location l) {
        checkNotNull(checkNotNull(l).getWorld());
        RegionManager rgm = WGUtils.getRegionManagerWithWorld(l.getWorld());
        String psID = WGUtils.matchLocationToPSID(l);
        ProtectedRegion r = rgm.getRegion(psID);
        if (r == null) {
            return null;
        } else {
            return new PSRegion(r, rgm, l.getWorld());
        }
    }

    /**
     * Get the protection stone region with the world and region.
     *
     * @param w the world
     * @param r the WorldGuard region
     * @return the {@link PSRegion} based on the parameters, or null if the region given is not a protectionstones region
     */
    public static PSRegion fromWGRegion(World w, ProtectedRegion r) {
        if (!ProtectionStones.isPSRegion(checkNotNull(r))) return null;
        return new PSRegion(r, WGUtils.getRegionManagerWithWorld(checkNotNull(w)), w);
    }

    /**
     * Get the protection stone regions that have the given name as their set nickname (/ps name)
     *
     * @param w    the world to look for regions in
     * @param name the nickname of the region
     * @return the list of regions that have that name
     */

    public static List<PSRegion> fromName(World w, String name) {
        List<PSRegion> l = new ArrayList<>();

        if (ProtectionStones.regionNameToID.get(w).get(name) == null) return l;

        for (int i = 0; i < ProtectionStones.regionNameToID.get(w).get(name).size(); i++) {
            String id = ProtectionStones.regionNameToID.get(w).get(name).get(i);
            if (WGUtils.getRegionManagerWithWorld(w).getRegion(id) == null) { // cleanup cache
                ProtectionStones.regionNameToID.get(w).get(name).remove(i);
                i--;
            } else {
                l.add(fromWGRegion(w, WGUtils.getRegionManagerWithWorld(w).getRegion(id)));
            }
        }
        return l;
    }

    // ~~~~~~~~~~~ instance ~~~~~~~~~~~~~~~~

    /**
     * @return gets the world that the region is in
     */
    public World getWorld() {
        return world;
    }

    /**
     * Get the WorldGuard ID of the region. Note that this is not guaranteed to be unique between worlds.
     *
     * @return the id of the region
     */
    public String getID() {
        return wgregion.getId();
    }

    /**
     * Get the name (nickname) of the region from /ps name.
     *
     * @return the name of the region, or null if the region does not have a name
     */

    public String getName() {
        return wgregion.getFlag(FlagHandler.PS_NAME);
    }

    /**
     * Set the name of the region (from /ps name).
     * @param name new name, or null to remove the name
     */

    public void setName(String name) {
        HashMap<String, ArrayList<String>> m = ProtectionStones.regionNameToID.get(getWorld());
        if (m.get(getName()) != null) {
            m.get(getName()).remove(getID());
        }
        if (name != null) {
            if (m.containsKey(name)) {
                m.get(name).add(getID());
            } else {
                m.put(name, new ArrayList<>(Collections.singletonList(getID())));
            }
        }
        wgregion.setFlag(FlagHandler.PS_NAME, name);
    }

    /**
     * Set the parent of this region.
     * @param r the region to be the parent, or null for no parent
     * @throws ProtectedRegion.CircularInheritanceException thrown when the parent already inherits from the child
     */

    public void setParent(PSRegion r) throws ProtectedRegion.CircularInheritanceException {
        wgregion.setParent(r == null ? null : r.getWGRegion());
    }

    /**
     * Get the parent of this region, if there is one.
     * @return the parent of the region, or null if there isn't one
     */

    public PSRegion getParent() {
        return wgregion.getParent() == null ? null : fromWGRegion(world, wgregion.getParent());
    }

    /**
     * Get the location of the set home the region has (for /ps tp).
     *
     * @return the location of the home, or null if the ps_home flag is not set.
     */
    public Location getHome() {
        String oPos = wgregion.getFlag(FlagHandler.PS_HOME);
        if (oPos == null) return null;
        String[] pos = oPos.split(" ");
        return new Location(world, Integer.parseInt(pos[0]), Integer.parseInt(pos[1]), Integer.parseInt(pos[2]));
    }

    /**
     * Set the home of the region (internally changes the flag).
     *
     * @param blockX block x location
     * @param blockY block y location
     * @param blockZ block z location
     */
    public void setHome(int blockX, int blockY, int blockZ) {
        wgregion.setFlag(FlagHandler.PS_HOME, blockX + " " + blockY + " " + blockZ);
    }

    /**
     * @return whether or not the protection block is hidden (/ps hide)
     */
    public boolean isHidden() {
        return !this.getProtectBlock().getType().toString().equals(this.getType());
    }

    /**
     * Hides the protection block, if it is not hidden.
     *
     * @return whether or not the block was hidden
     */
    public boolean hide() {
        if (!isHidden()) {
            getProtectBlock().setType(Material.AIR);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Unhides the protection block, if it is hidden.
     *
     * @return whether or not the block was unhidden
     */
    public boolean unhide() {
        if (isHidden()) {
            getProtectBlock().setType(Material.getMaterial(getType()));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Toggle whether or not the protection block is hidden.
     */
    public void toggleHide() {
        if (!hide()) unhide();
    }

    /**
     * This method returns the block that is supposed to contain the protection block.
     * Warning: If the protection stone is hidden, this will give the block that took its place!
     *
     * @return returns the block that may contain the protection stone
     */
    public Block getProtectBlock() {
        PSLocation psl = WGUtils.parsePSRegionToLocation(wgregion.getId());
        return world.getBlockAt(psl.x, psl.y, psl.z);
    }

    /**
     * @return returns the type
     */
    public PSProtectBlock getTypeOptions() {
        return ProtectionStones.getBlockOptions(wgregion.getFlag(FlagHandler.PS_BLOCK_MATERIAL));
    }

    /**
     * @return returns the protect block type that the region is
     */
    public String getType() {
        return wgregion.getFlag(FlagHandler.PS_BLOCK_MATERIAL);
    }

    /**
     * Get whether or not a player is an owner of this region.
     *
     * @param uuid the player's uuid
     * @return whether or not the player is a member
     */

    public boolean isOwner(UUID uuid) {
        return wgregion.getOwners().contains(uuid);
    }

    /**
     * Get whether or not a player is a member of this region.
     *
     * @param uuid the player's uuid
     * @return whether or not the player is a member
     */

    public boolean isMember(UUID uuid) {
        return wgregion.getMembers().contains(uuid);
    }

    /**
     * @return returns a list of the owners of the protected region
     */
    public ArrayList<UUID> getOwners() {
        return new ArrayList<>(wgregion.getOwners().getUniqueIds());
    }

    /**
     * @return returns a list of the members of the protected region
     */
    public ArrayList<UUID> getMembers() {
        return new ArrayList<>(wgregion.getMembers().getUniqueIds());
    }

    /**
     * Deletes the region forever. Can be cancelled by event cancellation.
     *
     * @param deleteBlock whether or not to also set the protection block to air (if not hidden)
     * @return whether or not the region was able to be successfully removed
     */
    public boolean deleteRegion(boolean deleteBlock) {

        PSRemoveEvent event = new PSRemoveEvent(this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) { // if event was cancelled, prevent execution
            return false;
        }

        if (deleteBlock && !this.isHidden()) {
            this.getProtectBlock().setType(Material.AIR);
        }
        rgmanager.removeRegion(wgregion.getId());
        return true;
    }

    /**
     * @return returns the WorldGuard region object directly
     */
    public ProtectedRegion getWGRegion() {
        return wgregion;
    }

    /**
     * @return returns the WorldGuard region manager that stores this region
     */
    public RegionManager getWGRegionManager() {
        return rgmanager;
    }
}
