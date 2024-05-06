/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fml.javafmlmod;

import java.lang.annotation.ElementType;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.JarVersionLookupHandler;
import net.neoforged.neoforgespi.IIssueReporting;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import net.neoforged.neoforgespi.language.ModFileScanData;
import net.neoforged.neoforgespi.locating.IModFile;

public class FMLJavaModLanguageProvider implements IModLanguageLoader {
    @Override
    public String name() {
        return "javafml";
    }

    @Override
    public ModContainer loadMod(IModInfo info, ModFileScanData modFileScanResults, ModuleLayer layer) {
        final var modClasses = modFileScanResults.getAnnotatedBy(Mod.class, ElementType.TYPE)
                .filter(data -> data.annotationData().get("value").equals(info.getModId()))
                .filter(ad -> AutomaticEventSubscriber.getSides(ad.annotationData().get("dist")).contains(FMLLoader.getDist()))
                .map(ad -> ad.clazz().getClassName())
                .toList();
        return new FMLModContainer(info, modClasses, modFileScanResults, layer);
    }

    @Override
    public void validate(IModFile file, Collection<ModContainer> loadedContainers, IIssueReporting reporter) {
        final Set<String> modIds = new HashSet<>();
        for (IModInfo modInfo : file.getModInfos()) {
            if (modInfo.getLoader() == this) {
                modIds.add(modInfo.getModId());
            }
        }

        file.getScanResult().getAnnotatedBy(Mod.class, ElementType.TYPE)
                .filter(data -> !modIds.contains((String) data.annotationData().get("value")))
                .forEach(data -> {
                    var modId = data.annotationData().get("value");
                    var entrypointClass = data.clazz().getClassName();
                    var issue = ModLoadingIssue.error("fml.modloading.javafml.dangling_entrypoint", modId, entrypointClass, file.getFilePath()).withAffectedModFile(file);
                    reporter.addIssue(issue);
                });
    }

    @Override
    public String version() {
        final Path lpPath;
        try {
            lpPath = Paths.get(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Huh?", e);
        }
        return JarVersionLookupHandler.getVersion(this.getClass()).orElse(Files.isDirectory(lpPath) ? FMLLoader.versionInfo().fmlVersion() : null);
    }
}
