--------------------------------------------------------------------------------
--                                IEEE2Flopoco
--                           (InputIEEE_4_3_to_4_3)
-- This operator is part of the Infinite Virtual Library FloPoCoLib
-- All rights reserved 
-- Authors: Florent de Dinechin (2008)
--------------------------------------------------------------------------------
-- Pipeline depth: 0 cycles

library ieee;
use ieee.std_logic_1164.all;
use ieee.std_logic_arith.all;
use ieee.std_logic_unsigned.all;
library std;
use std.textio.all;
library work;

entity IEEE2Flopoco is
   port ( clk, rst : in std_logic;
          X : in  std_logic_vector(7 downto 0);
          R : out  std_logic_vector(4+3+2 downto 0)   );
end entity;

architecture arch of IEEE2Flopoco is
signal expX :  std_logic_vector(3 downto 0);
signal fracX :  std_logic_vector(2 downto 0);
signal sX :  std_logic;
signal expZero :  std_logic;
signal expInfty :  std_logic;
signal fracZero :  std_logic;
signal reprSubNormal :  std_logic;
signal sfracX :  std_logic_vector(2 downto 0);
signal fracR :  std_logic_vector(2 downto 0);
signal expR :  std_logic_vector(3 downto 0);
signal infinity :  std_logic;
signal zero :  std_logic;
signal NaN :  std_logic;
signal exnR :  std_logic_vector(1 downto 0);
begin
   process(clk)
      begin
         if clk'event and clk = '1' then
         end if;
      end process;
   expX  <= X(6 downto 3);
   fracX  <= X(2 downto 0);
   sX  <= X(7);
   expZero  <= '1' when expX = (3 downto 0 => '0') else '0';
   expInfty  <= '1' when expX = (3 downto 0 => '1') else '0';
   fracZero <= '1' when fracX = (2 downto 0 => '0') else '0';
   reprSubNormal <= fracX(2);
   -- since we have one more exponent value than IEEE (field 0...0, value emin-1),
   -- we can represent subnormal numbers whose mantissa field begins with a 1
   sfracX <= fracX(1 downto 0) & '0' when (expZero='1' and reprSubNormal='1')    else fracX;
   fracR <= sfracX;
   -- copy exponent. This will be OK even for subnormals, zero and infty since in such cases the exn bits will prevail
   expR <= expX;
   infinity <= expInfty and fracZero;
   zero <= expZero and not reprSubNormal;
   NaN <= expInfty and not fracZero;
   exnR <= 
           "00" when zero='1' 
      else "10" when infinity='1' 
      else "11" when NaN='1' 
      else "01" ;  -- normal number
   R <= exnR & sX & expR & fracR; 
end architecture;

--------------------------------------------------------------------------------
--                                Flopoco2IEEE
--                          (OutputIEEE_4_3_to_4_3)
-- This operator is part of the Infinite Virtual Library FloPoCoLib
-- All rights reserved 
-- Authors: F. Ferrandi  (2009-2012)
--------------------------------------------------------------------------------
-- Pipeline depth: 0 cycles

library ieee;
use ieee.std_logic_1164.all;
use ieee.std_logic_arith.all;
use ieee.std_logic_unsigned.all;
library std;
use std.textio.all;
library work;

entity Flopoco2IEEE is
   port ( clk, rst : in std_logic;
          X : in  std_logic_vector(4+3+2 downto 0);
          R : out  std_logic_vector(7 downto 0)   );
end entity;

architecture arch of Flopoco2IEEE is
signal expX :  std_logic_vector(3 downto 0);
signal fracX :  std_logic_vector(2 downto 0);
signal exnX :  std_logic_vector(1 downto 0);
signal sX :  std_logic;
signal expZero :  std_logic;
signal sfracX :  std_logic_vector(2 downto 0);
signal fracR :  std_logic_vector(2 downto 0);
signal expR :  std_logic_vector(3 downto 0);
begin
   process(clk)
      begin
         if clk'event and clk = '1' then
         end if;
      end process;
   expX  <= X(6 downto 3);
   fracX  <= X(2 downto 0);
   exnX  <= X(9 downto 8);
   sX  <= X(7) when (exnX = "01" or exnX = "10" or exnX = "00") else '0';
   expZero  <= '1' when expX = (3 downto 0 => '0') else '0';
   -- since we have one more exponent value than IEEE (field 0...0, value emin-1),
   -- we can represent subnormal numbers whose mantissa field begins with a 1
   sfracX <= 
      (2 downto 0 => '0') when (exnX = "00") else
      '1' & fracX(2 downto 1) when (expZero = '1' and exnX = "01") else
      fracX when (exnX = "01") else 
      (2 downto 1 => '0') & exnX(0);
   fracR <= sfracX;
   expR <=  
      (3 downto 0 => '0') when (exnX = "00") else
      expX when (exnX = "01") else 
      (3 downto 0 => '1');
   R <= sX & expR & fracR; 
end architecture;

