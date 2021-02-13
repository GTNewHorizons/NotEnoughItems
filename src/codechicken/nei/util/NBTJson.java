package codechicken.nei.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NBTJson {
    private static final Pattern numberPattern = Pattern.compile("^([-+]?[\\d]+\\.?[0-9]*)([bBsSlLfFdD]?)$");
    private static final JsonParser parser = new JsonParser();
    private static final Gson gson = new GsonBuilder().create();

    public static String toJson(NBTTagCompound tag) {
        final String jsonString = toJsonObject(tag).toString();
        return gson.toJson(parser.parse(jsonString));
    }
    
    @SuppressWarnings("unchecked")
    public static JsonElement toJsonObject(NBTBase nbt) {
        if (nbt instanceof NBTTagCompound) {
            // NBTTagCompound
            final NBTTagCompound nbtTagCompound = (NBTTagCompound) nbt;
            final Map<String, NBTBase> tagMap = (Map<String, NBTBase>)nbtTagCompound.tagMap;
            
            JsonObject root = new JsonObject();
            for (Map.Entry<String, NBTBase> nbtEntry : tagMap.entrySet() ) {
                root.add(nbtEntry.getKey(), toJsonObject(nbtEntry.getValue()));
            }
            return root;
        } else if (nbt instanceof NBTBase.NBTPrimitive) {
            // Number
            return new JsonPrimitive(((NBTBase.NBTPrimitive) nbt).func_150286_g());
        } else if (nbt instanceof NBTTagString) {
            // String
           return new JsonPrimitive(((NBTTagString) nbt).func_150285_a_());
        } else if (nbt instanceof NBTTagList) {
            // Tag List
            JsonArray arr = new JsonArray();
            ((NBTTagList) nbt).tagList.forEach(c -> arr.add(toJsonObject((NBTBase) c)));
            return arr;
        } else if (nbt instanceof NBTTagIntArray) {
            // Int Array
            JsonArray arr = new JsonArray();
            for(int i : ((NBTTagIntArray) nbt).func_150302_c()) {
                arr.add(new JsonPrimitive(i));
            }
            return arr;
        } else {
            throw new IllegalArgumentException("Unsupported NBT Tag: " + NBTBase.NBTTypes[nbt.getId()] + " - " + nbt);
        }
    }
    
    public static NBTBase toNbt(JsonElement jsonElement) {
        if (jsonElement instanceof JsonPrimitive) {
            // Number or String
            final JsonPrimitive jsonPrimitive = (JsonPrimitive) jsonElement;
            final String jsonString = jsonPrimitive.getAsString();
            final Matcher m = numberPattern.matcher(jsonString);
            if(m.find()) {
                // Number
                final String numberString = m.group(1);
                if (m.groupCount() == 2 && m.group(2).length() > 0) {
                    final char numberType = m.group(2).charAt(0);
                    switch(numberType) {
                        case 'b':
                        case 'B':
                            return new NBTTagByte(Byte.parseByte(numberString));
                        case 's':
                        case 'S':
                            return new NBTTagShort(Short.parseShort(numberString));
                        case 'l':
                        case 'L':
                            return new NBTTagLong(Long.parseLong(numberString));
                        case 'f':
                        case 'F':
                            return new NBTTagFloat(Float.parseFloat(numberString));
                        case 'd':
                        case 'D':
                            return new NBTTagDouble(Double.parseDouble(numberString));
                    }
                } else {
                    if (numberString.contains("."))
                        return new NBTTagDouble(Double.parseDouble(numberString));
                    else
                        return new NBTTagInt(Integer.parseInt(numberString));
                }
            } else {
                // String
                return new NBTTagString(jsonString);
            }
        }  else if (jsonElement instanceof JsonArray) {
            // NBTTagIntArray or NBTTagList
            final JsonArray jsonArray = (JsonArray) jsonElement;
            final List<NBTBase> nbtList = new ArrayList<>();
           
            for (JsonElement element : jsonArray) {
                nbtList.add(toNbt(element));
            }
            
            if (nbtList.stream().allMatch(n -> n instanceof NBTTagInt)) {
                return new NBTTagIntArray(nbtList.stream().mapToInt(i -> ((NBTTagInt)i).func_150287_d() ).toArray());
            } else {
                NBTTagList nbtTagList = new NBTTagList();
                nbtList.forEach(nbtTagList::appendTag);

                return nbtTagList;

            }
        } else if (jsonElement instanceof JsonObject) {
            // NBTTagCompound
            final JsonObject jsonObject = (JsonObject) jsonElement;
            NBTTagCompound nbtTagCompound = new NBTTagCompound();
            for (Map.Entry<String, JsonElement> jsonEntry : jsonObject.entrySet()) {
                nbtTagCompound.setTag(jsonEntry.getKey(), toNbt(jsonEntry.getValue()));
            }
            return nbtTagCompound;
        }
        
        throw new IllegalArgumentException("Unhandled element " + jsonElement);
    }
    
}
