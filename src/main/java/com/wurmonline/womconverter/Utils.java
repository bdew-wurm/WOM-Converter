package com.wurmonline.womconverter;

import org.lwjgl.assimp.AINode;

public class Utils {
    public static String sanitizeNodeName(AINode node, AINode root) {
        if (node == null) return "";
        if (node.address() == root.address()) return "root";
        String name = node.mName().dataString();
        if (name.startsWith("wom-")) name = name.substring(4);
        return name;
    }
}
