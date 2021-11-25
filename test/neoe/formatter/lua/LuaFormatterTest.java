package neoe.formatter.lua;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LuaFormatterTest {
    @Test
    public void testMethodArgumentsFormatting() throws Exception {
        String code = "function onCanRx(bus, id)\n" +
                "end";
        String formatted = new LuaFormatter().format(code, new LuaFormatter.Env());
        assertEquals("function onCanRx(bus, id)\n" +
                "end\n" +
                "\n", formatted);
    }

    @Test
    public void testArrayFormatting() throws Exception {
        String code = "function onCanRx()\n" +
                "    canState = data[1]\n" +
                "end";

        String formatted = new LuaFormatter().format(code, new LuaFormatter.Env());
        assertEquals("function onCanRx()\n" +
                "\tcanState = data[1]\n" +
                "end\n" +
                "\n", formatted);


    }
}
