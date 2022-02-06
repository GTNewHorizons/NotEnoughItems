package codechicken.nei.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
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
    private static final Pattern numberPattern = Pattern.compile("^([-+]?\\d+\\.?\\d*E*\\d*)([bBsSlLfFdD]?)$");

    public static String toJson(NBTTagCompound tag) {
        return toJson(toJsonObject(tag));
    }

    public static String toJson(JsonElement json) {
        return json.toString();
    }
    
    @SuppressWarnings("unchecked")
    public static JsonElement toJsonObject(NBTBase nbt) {
        if (nbt instanceof NBTTagCompound) {
            // NBTTagCompound
            final NBTTagCompound nbtTagCompound = (NBTTagCompound) nbt;
            final Map<String, NBTBase> tagMap = (Map<String, NBTBase>)nbtTagCompound.tagMap;
            
            JsonObject root = new JsonObject();

            for (Map.Entry<String, NBTBase> nbtEntry : tagMap.entrySet()) {
                root.add(nbtEntry.getKey(), toJsonObject(nbtEntry.getValue()));
            }

            return root;
        } else if (nbt instanceof NBTTagByte) {
            // Number (byte)
            return new JsonPrimitive(Byte.toString(((NBTTagByte) nbt).func_150290_f()) + 'B');
        } else if (nbt instanceof NBTTagShort) {
            // Number (short)
            return new JsonPrimitive(Short.toString(((NBTTagShort) nbt).func_150289_e()) + 'S');
        } else if (nbt instanceof NBTTagInt) {
            // Number (int)
            return new JsonPrimitive(((NBTTagInt) nbt).func_150287_d());
        } else if (nbt instanceof NBTTagLong) {
            // Number (long)
            return new JsonPrimitive(Long.toString(((NBTTagLong) nbt).func_150291_c()) + 'L');
        } else if (nbt instanceof NBTTagFloat) {
            // Number (float)
            return new JsonPrimitive(Float.toString(((NBTTagFloat) nbt).func_150288_h()) + 'F');
        } else if (nbt instanceof NBTTagDouble) {
           // Number (double)
           return new JsonPrimitive(Double.toString(((NBTTagDouble) nbt).func_150286_g()) + 'D');
        } else if (nbt instanceof NBTBase.NBTPrimitive) {
            // Number
            return new JsonPrimitive(((NBTBase.NBTPrimitive) nbt).func_150286_g());
        } else if (nbt instanceof NBTTagString) {
            // String
           return new JsonPrimitive(((NBTTagString) nbt).func_150285_a_());
        } else if (nbt instanceof NBTTagList) {
            // Tag List
            final NBTTagList list = (NBTTagList) nbt;

            if (list.tagList.isEmpty()) {
                return createEmptyList(list);
            } else {
                JsonArray arr = new JsonArray();
                list.tagList.forEach(c -> arr.add(toJsonObject((NBTBase) c)));
                return arr;
            }
     
        } else if (nbt instanceof NBTTagIntArray) {
            // Int Array
            final NBTTagIntArray list = (NBTTagIntArray) nbt;

            if (list.func_150302_c().length == 0) {
                return createEmptyList(list);
            } else {
                JsonArray arr = new JsonArray();

                for (int i : list.func_150302_c()) {
                    arr.add(new JsonPrimitive(i));
                }

                return arr;
            }

        } else if (nbt instanceof NBTTagByteArray) {
            // Byte Array
            final NBTTagByteArray list = (NBTTagByteArray) nbt;

            if (list.func_150292_c().length == 0) {
                return createEmptyList(list);
            } else {
                JsonArray arr = new JsonArray();

                for (byte i : list.func_150292_c()) {
                    arr.add(new JsonPrimitive(Byte.toString(i) + 'B'));
                }

                return arr;
            }

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
            if (m.find()) {
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
            } else if (nbtList.stream().allMatch(n -> n instanceof NBTTagByte)) {
                final byte[] abyte = new byte[nbtList.size()];

                for (int i = 0; i < nbtList.size(); i++) {
                    abyte[i] = ((NBTTagByte) nbtList.get(i)).func_150290_f();
                }
                
                return new NBTTagByteArray(abyte);
            } else {
                NBTTagList nbtTagList = new NBTTagList();
                nbtList.forEach(nbtTagList::appendTag);

                return nbtTagList;

            }
        } else if (jsonElement instanceof JsonObject) {
            // NBTTagCompound
            final JsonObject jsonObject = (JsonObject) jsonElement;
            final NBTBase custom = restoreEmptyList(jsonObject);

            if (custom == null) {
                NBTTagCompound nbtTagCompound = new NBTTagCompound();

                for (Map.Entry<String, JsonElement> jsonEntry : jsonObject.entrySet()) {
                    nbtTagCompound.setTag(jsonEntry.getKey(), toNbt(jsonEntry.getValue()));
                }
    
                return nbtTagCompound;
            } else {
                return custom;
            }

        }
        
        throw new IllegalArgumentException("Unhandled element " + jsonElement);
    }

    protected static JsonObject createEmptyList(Object obj)
    {
        JsonObject empty = new JsonObject();
        empty.add("__custom_type", new JsonPrimitive(obj.getClass().getName()));
        return empty;
    }

    protected static NBTBase restoreEmptyList(JsonObject obj)
    {

        if (obj.has("__custom_type")) {
            try {
                final String className = obj.get("__custom_type").getAsString();
                return (NBTBase)Class.forName(className).newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException th) {}
        }

        return null;
    }
    
}
