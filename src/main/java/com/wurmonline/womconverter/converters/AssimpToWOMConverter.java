package com.wurmonline.womconverter.converters;

import com.google.common.io.LittleEndianDataOutputStream;
import com.wurmonline.womconverter.ConversionFailedException;
import com.wurmonline.womconverter.MatReporter;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

public class AssimpToWOMConverter {

    private static final String FLOATS_FORMAT = "%.4f";

    public static void convert(File inputFile, File outputDirectory, boolean generateTangents, Properties forceMats, MatReporter matReport, boolean fixMeshNames) throws MalformedURLException, IOException, ConversionFailedException {
        if (inputFile == null || outputDirectory == null) {
            throw new IllegalArgumentException("Input file and/or output directory cannot be null");
        } else if (!outputDirectory.isDirectory()) {
            throw new IllegalArgumentException("Output directory is not a directory");
        }

        System.out.println("------------------------------------------------------------------------");
        System.out.println("Converting file: " + inputFile.getName() + ", output directory: " + outputDirectory.getAbsolutePath());

        String modelFileName = inputFile.getName();
        modelFileName = modelFileName.substring(0, modelFileName.lastIndexOf('.'));

        int flags;
        if (generateTangents) {
            flags = Assimp.aiProcess_JoinIdenticalVertices | Assimp.aiProcess_Triangulate | Assimp.aiProcess_CalcTangentSpace;
        } else {
            flags = Assimp.aiProcess_JoinIdenticalVertices | Assimp.aiProcess_Triangulate;
        }
        AIScene scene = Assimp.aiImportFile(inputFile.getAbsolutePath(), flags);

        if (scene == null) {
            System.err.printf("Failed to load scene from %s - %s%n", inputFile.getName(), Assimp.aiGetErrorString());
            return;
        }

        LittleEndianDataOutputStream output = new LittleEndianDataOutputStream(new FileOutputStream(new File(outputDirectory, modelFileName + ".wom")));

        PointerBuffer materialsPointer = scene.mMaterials();
        AIMaterial[] materials = new AIMaterial[scene.mNumMaterials()];
        for (int i = 0; i < scene.mNumMaterials(); i++) {
            materials[i] = AIMaterial.create(materialsPointer.get(i));
        }

        PointerBuffer meshesPointer = scene.mMeshes();
        AIMesh[] meshes = new AIMesh[scene.mNumMeshes()];
        for (int i = 0; i < scene.mNumMeshes(); i++) {
            meshes[i] = AIMesh.create(meshesPointer.get(i));
        }

        int meshesCount = scene.mNumMeshes();
        output.writeInt(meshesCount);

        HashMap<String, Integer> meshCounter = null;
        if (fixMeshNames) meshCounter = new HashMap<>();

        for (int i = 0; i < meshesCount; i++) {
            if (fixMeshNames) {
                String tex = getMaterialTexture(materials[meshes[i].mMaterialIndex()]);
                if (tex.contains(".")) tex = tex.substring(0, tex.indexOf('.'));
                int n = meshCounter.getOrDefault(tex, 1);
                meshCounter.put(tex, n + 1);
                writeMesh(output, meshes[i], String.format("%s-%d", tex, n));
            } else {
                writeMesh(output, meshes[i], null);
            }

            int materialCount = 1;
            output.writeInt(materialCount);
            writeMaterial(output, materials[meshes[i].mMaterialIndex()], forceMats, matReport);
        }

        ArrayList<AINode> nodesToWrite = new ArrayList<>();

        AINode root = scene.mRootNode();
        if (root != null) {
            System.out.println(String.format("Checking nodes - root: %s (%d children / %d meshes)", root.mName().dataString(), root.mNumChildren(), root.mNumMeshes()));
            if (root.mChildren() != null) {
                for (int i = 0; i < root.mNumChildren(); i++) {
                    AINode child = AINode.create(Objects.requireNonNull(root.mChildren()).get(i));
                    boolean write = child.mName().dataString().startsWith("wom-");
                    System.out.println(String.format("%s %s (%d children / %d meshes)", write ? "+" : "-", child.mName().dataString(), child.mNumChildren(), child.mNumMeshes()));
                    if (write) {
                        nodesToWrite.add(child);
                        AIMatrix4x4 trans = child.mTransformation();
                        System.out.println(String.format(" -> %f %f %f %f", trans.a1(), trans.a2(), trans.a3(), trans.a4()));
                        System.out.println(String.format(" -> %f %f %f %f", trans.b1(), trans.b2(), trans.b3(), trans.b4()));
                        System.out.println(String.format(" -> %f %f %f %f", trans.c1(), trans.c2(), trans.c3(), trans.c4()));
                        System.out.println(String.format(" -> %f %f %f %f", trans.d1(), trans.d2(), trans.d3(), trans.d4()));
                    }
                }
            }
        }

        output.writeInt(nodesToWrite.size());
        for (AINode node : nodesToWrite) {
            writeString(output, "");
            writeString(output, node.mName().dataString().substring(4));
            output.write(0);
            AIMatrix4x4 trans = node.mTransformation();
            output.writeFloat(trans.a1());
            output.writeFloat(trans.a2());
            output.writeFloat(trans.a3());
            output.writeFloat(trans.a4());
            output.writeFloat(trans.b1());
            output.writeFloat(trans.b2());
            output.writeFloat(trans.b3());
            output.writeFloat(trans.b4());
            output.writeFloat(trans.c1());
            output.writeFloat(trans.c2());
            output.writeFloat(trans.c3());
            output.writeFloat(trans.c4());
            output.writeFloat(trans.d1());
            output.writeFloat(trans.d2());
            output.writeFloat(trans.d3());
            output.writeFloat(trans.d4());
            for (int i = 0; i < 16; i++) output.writeFloat(0f);
        }

        for (int i = 0; i < meshesCount; i++) {
            boolean hasSkinning = false;
            output.write(hasSkinning ? 1 : 0);
            // skinning exporting here
        }

        output.close();

        System.out.println("File converted: " + inputFile.getName() + ", output directory: " + outputDirectory.getAbsolutePath());

        if (matReport != null) matReport.reportFile(inputFile.getName());
    }

    private static void writeMesh(LittleEndianDataOutputStream output, AIMesh mesh, String nameOverride) throws IOException, ConversionFailedException {
        boolean hasTangents = mesh.mTangents() != null;
        output.write(hasTangents ? 1 : 0);
        boolean hasBinormal = mesh.mBitangents() != null;
        output.write(hasBinormal ? 1 : 0);
        boolean hasVertexColor = mesh.mColors(0) != null;
        output.write(hasVertexColor ? 1 : 0);

        String name = mesh.mName().dataString();

        if (nameOverride != null) {
            writeString(output, nameOverride);
            System.out.println("Mesh name override:\t" + nameOverride);
        } else {
            writeString(output, name);
            System.out.println("Mesh name:\t" + name);
        }

        System.out.println("Has tangents:\t" + hasTangents);
        System.out.println("Has binormals:\t" + hasBinormal);
        System.out.println("Has colors:\t" + hasVertexColor);

        int verticesCount = mesh.mNumVertices();
        output.writeInt(verticesCount);
        System.out.println("Vertices:\t" + verticesCount);

        for (int i = 0; i < verticesCount; i++) {
            AIVector3D vertex = mesh.mVertices().get(i);
            output.writeFloat(vertex.x());
            output.writeFloat(vertex.y());
            output.writeFloat(vertex.z());

            AIVector3D normal = mesh.mNormals().get(i);
            output.writeFloat(normal.x());
            output.writeFloat(normal.y());
            output.writeFloat(normal.z());

            AIVector3D uv = mesh.mTextureCoords(0).get(i);
            output.writeFloat(uv.x());
            output.writeFloat(1 - uv.y());

            if (hasVertexColor) {
                AIColor4D.Buffer color = mesh.mColors(i);
                output.writeFloat(color.r());
                output.writeFloat(color.g());
                output.writeFloat(color.b());
            }

            if (hasTangents) {
                AIVector3D tangent = mesh.mTangents().get(i);
                output.writeFloat(tangent.x());
                output.writeFloat(tangent.y());
                output.writeFloat(tangent.z());
            }

            if (hasBinormal) {
                AIVector3D binormal = mesh.mBitangents().get(i);
                output.writeFloat(binormal.x());
                output.writeFloat(binormal.y());
                output.writeFloat(binormal.z());
            }
        }

        int facesCount = mesh.mNumFaces();
        System.out.println("Faces:\t\t" + facesCount);
        System.out.println("Triangles:\t" + (facesCount * 3));

        List<int[]> goodFaces = new ArrayList<>();
        int skipped = 0;

        for (int i = 0; i < facesCount; i++) {
            AIFace face = mesh.mFaces().get(i);
            if (face.mNumIndices() != 3) {
                skipped++;
                continue;
            }
            if (face.mIndices().get(0) > Short.MAX_VALUE || face.mIndices().get(1) > Short.MAX_VALUE || face.mIndices().get(2) > Short.MAX_VALUE)
                throw new ConversionFailedException(String.format("mesh %s has too many vertices and can't be represented correctly in WOM", name));
            goodFaces.add(new int[]{face.mIndices().get(0), face.mIndices().get(1), face.mIndices().get(2)});
        }

        if (skipped > 0)
            System.err.println(String.format("Warning: mesh %s has %d face%s that's not a triangle, this doesn't work in wom", name, skipped, skipped > 1 ? "s" : ""));

        output.writeInt(goodFaces.size() * 3);

        for (int[] face : goodFaces) {
            output.writeShort(face[0]);
            output.writeShort(face[1]);
            output.writeShort(face[2]);
        }

        System.out.println("");
    }

    private static String getMaterialTexture(AIMaterial material) {
        AIString textureNameNative = AIString.create();
        Assimp.aiGetMaterialString(material, Assimp._AI_MATKEY_TEXTURE_BASE, Assimp.aiTextureType_DIFFUSE, 0, textureNameNative);
        String textureName = textureNameNative.dataString();
        return textureName.substring(Math.max(textureName.lastIndexOf("/"), textureName.lastIndexOf("\\")) + 1);
    }

    private static void writeMaterial(LittleEndianDataOutputStream output, AIMaterial material, Properties forceMats, MatReporter matReport) throws IOException {
        String textureName = getMaterialTexture(material);
        writeString(output, textureName);

        AIString materialNameNative = AIString.create();
        Assimp.aiGetMaterialString(material, Assimp.AI_MATKEY_NAME, 0, 0, materialNameNative);
        String materialName = materialNameNative.dataString();
        if (forceMats.containsKey(textureName))
            materialName = forceMats.getProperty(textureName);
        writeString(output, materialName);

        if (matReport != null)
            matReport.addMat(materialName, textureName);

        System.out.println("Material name:\t" + materialName);
        System.out.println("Texture path:\t" + textureName);

        boolean isEnabled = true;
        output.write(isEnabled ? 1 : 0);

        boolean propertyExists = true;

        output.write(propertyExists ? 1 : 0);
        AIColor4D emissive = AIColor4D.create();
        Assimp.aiGetMaterialColor(material, Assimp.AI_MATKEY_COLOR_EMISSIVE, 0, 0, emissive);
        System.out.println("Emissive:\t" + String.format(FLOATS_FORMAT, emissive.r()) + "\t" + String.format(FLOATS_FORMAT, emissive.g()) + "\t" + String.format(FLOATS_FORMAT, emissive.b()) + "\t" + String.format(FLOATS_FORMAT, emissive.a()));
        output.writeFloat(emissive.r());
        output.writeFloat(emissive.g());
        output.writeFloat(emissive.b());
        output.writeFloat(emissive.a());

        output.write(propertyExists ? 1 : 0);
        FloatBuffer shininessBuffer = BufferUtils.createFloatBuffer(1);
        IntBuffer valuesCountBuffer = BufferUtils.createIntBuffer(1);
        valuesCountBuffer.put(1);
        valuesCountBuffer.rewind();
        Assimp.aiGetMaterialFloatArray(material, Assimp.AI_MATKEY_SHININESS, 0, 0, shininessBuffer, valuesCountBuffer);
        float shininess = shininessBuffer.get(0);
        System.out.println("Shininess:\t" + String.format(FLOATS_FORMAT, shininess));
        output.writeFloat(shininess);

        output.write(propertyExists ? 1 : 0);
        AIColor4D specular = AIColor4D.create();
        Assimp.aiGetMaterialColor(material, Assimp.AI_MATKEY_COLOR_SPECULAR, 0, 0, specular);
        System.out.println("Specular:\t" + String.format(FLOATS_FORMAT, specular.r()) + "\t" + String.format(FLOATS_FORMAT, specular.g()) + "\t" + String.format(FLOATS_FORMAT, specular.b()) + "\t" + String.format(FLOATS_FORMAT, specular.a()));
        output.writeFloat(specular.r());
        output.writeFloat(specular.g());
        output.writeFloat(specular.b());
        output.writeFloat(specular.a());

        output.write(propertyExists ? 1 : 0);
        AIColor4D transparency = AIColor4D.create();
        Assimp.aiGetMaterialColor(material, Assimp.AI_MATKEY_COLOR_TRANSPARENT, 0, 0, transparency);
        System.out.println("Transparency:\t" + String.format(FLOATS_FORMAT, transparency.r()) + "\t" + String.format(FLOATS_FORMAT, transparency.g()) + "\t" + String.format(FLOATS_FORMAT, transparency.b()) + "\t" + String.format(FLOATS_FORMAT, transparency.a()));
        output.writeFloat(transparency.r());
        output.writeFloat(transparency.g());
        output.writeFloat(transparency.b());
        output.writeFloat(transparency.a());

        System.out.println("");
    }

    private static void writeString(LittleEndianDataOutputStream output, String str) throws IOException {
        byte[] chars = str.getBytes("UTF-8");

        output.writeInt(chars.length);
        for (int i = 0; i < chars.length; i++) {
            output.writeByte(chars[i]);
        }
    }

}
