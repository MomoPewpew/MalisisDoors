package net.malisis.doors.block;

import java.util.Random;

import net.malisis.doors.MalisisDoors;
import net.malisis.doors.entity.DoorTileEntity;
import net.malisis.doors.renderer.block.DoorRenderer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.IconFlipped;
import net.minecraft.client.renderer.texture.IconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Icon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class Door extends BlockContainer
{
	protected Icon[] iconTop;
	protected Icon[] iconBottom;
	protected Icon iconSide;
	protected String soundPath;

	public static final int DIR_WEST = 0;
	public static final int DIR_NORTH = 1;
	public static final int DIR_EAST = 2;
	public static final int DIR_SOUTH = 3;

	public static final float DOOR_WIDTH = 0.1875F;

	public static final int flagOpened = 1 << 2;
	public static final int flagTopBlock = 1 << 3;
	public static final int flagReversed = 1 << 4;

	public static final int stateClose = 0;
	public static final int stateClosing = 1;
	public static final int stateOpen = 2;
	public static final int stateOpening = 3;

	public static final int openingTime = 4;

	public Door(int id, Material material)
	{
		super(id, material);
		float f = 0.5F;
		float f1 = 1.0F;
		setBlockBounds(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, f1, 0.5F + f);
		disableStats();
		// wood
		if (material == Material.wood)
		{
			setHardness(3.0F);
			setStepSound(soundWoodFootstep);
			setUnlocalizedName("doorWood");
			disableStats();
			setTextureName("door_wood");
		}
		else
		{
			setHardness(5.0F);
			setStepSound(soundMetalFootstep);
			setUnlocalizedName("doorIron");
			setTextureName("door_iron");
		}
	}

	// #region Icons
	@SideOnly(Side.CLIENT)
	public Icon getIcon(int side, int metadata)
	{
		// return icons[(metadata & flagTopBlock) >> 3];
		if (side == 1 || side == 0)
			return iconSide;

		int dir = metadata & 3;
		boolean opened = (metadata & flagOpened) != 0;
		boolean topBlock = (metadata & flagTopBlock) != 0;
		boolean reversed = false;
		
		if(((dir == DIR_NORTH || dir == DIR_SOUTH) && !opened) || ((dir == DIR_WEST || dir == DIR_EAST) && opened))
		{
			//{DOWN, UP, NORTH, SOUTH, WEST, EAST}
			if(side == 4 || side == 5)
				return iconSide;
		}
		else if (side == 2 || side == 3)
			return iconSide;
		
		
		if (opened)
		{
			if (dir == DIR_WEST && side == 2)
				reversed = true;
			else if (dir == DIR_NORTH && side != 4)
				reversed = true;
			else if (dir == DIR_EAST && side == 3)
				reversed = true;
			else if (dir == DIR_SOUTH && side == 4)
				reversed = true;
		}
		else
		{
			if (dir == DIR_WEST && side == 5)
				reversed = true;
			else if (dir == DIR_NORTH && side == 3)
				reversed = true;
			else if (dir == DIR_EAST && side == 4)
				reversed = true;
			else if (dir == DIR_SOUTH && side == 2)
				reversed = true;

			if ((metadata & flagReversed) != 0)
				reversed = !reversed;
		}

		return topBlock ? iconTop[reversed ? 1 : 0] : iconBottom[reversed ? 1 : 0];
	}

	@SideOnly(Side.CLIENT)
	public Icon getBlockTexture(IBlockAccess world, int x, int y, int z, int side)
	{
		return getIcon(side, getFullMetadata(world, x, y, z));
	}

	@SideOnly(Side.CLIENT)
	public void registerIcons(IconRegister register)
	{
		iconTop = new Icon[2];
		iconBottom = new Icon[2];
		iconSide = register.registerIcon(MalisisDoors.modid + ":" + getTextureName() + "_side");
		iconTop[0] = register.registerIcon(getTextureName() + "_upper");
		iconBottom[0] = register.registerIcon(getTextureName() + "_lower");
		iconTop[1] = new IconFlipped(iconTop[0], true, false);
		iconBottom[1] = new IconFlipped(iconBottom[0], true, false);

	}

	// #end

	public void setDoorState(World world, int x, int y, int z, int state)
	{
		int metadata = getFullMetadata(world, x, y, z);
		y -= (metadata & flagTopBlock) != 0 ? 1 : 0;

		if (state == stateOpening || state == stateClosing)
		{
			if (!world.isRemote)
			{
				DoorTileEntity te = (DoorTileEntity) world.getBlockTileEntity(x, y, z);
				if (te != null)
					te.startAnimation(state);
				world.markBlockForUpdate(x, y, z);
			}
		}
		else
		{
			int bottomMetadata = metadata & 7;
			bottomMetadata = state == stateOpen ? bottomMetadata | flagOpened : bottomMetadata & ~flagOpened;

			// MalisisMod.Message((world.isRemote ? "[C]" : "[S]") + "Metadata set : " + bottomMetadata);
			world.setBlockMetadataWithNotify(x, y, z, bottomMetadata, 2);
			world.markBlockForUpdate(x, y, z);// , x, y, z);

			playOpenCloseSound(world, x, y, z);
		}
	}

	public void playOpenCloseSound(World world, int x, int y, int z)
	{
		if (soundPath != null)
			world.playSoundEffect(x, y, z, soundPath, 1F, 1F);
		else
			world.playSoundEffect(x, y, z, Math.random() < 0.5F ? "random.door_open" : "random.door_close", 1.0F, 1.0F);
	}

	/**
	 * Check if the bloc is part of double door
	 * @param world
	 * @param x
	 * @param y
	 * @param z
	 * @param metadata of the original door activated
	 * @return
	 */
	public boolean isDoubleDoor(World world, int x, int y, int z, int metadata)
	{
		int metadata2 = getFullMetadata(world, x, y, z);

		if (world.getBlockId(x, y, z) != blockID) // different block
			return false;

		if ((metadata & 3) != (metadata2 & 3)) // different direction
			return false;

		if ((metadata & flagOpened) != (metadata2 & flagOpened)) // different state
			return false;

		if ((metadata & flagReversed) == (metadata2 & flagReversed)) // handle same side
			return false;

		return true;
	}

	/**
	 * Open/close associated double door
	 * @param world
	 * @param x
	 * @param y
	 * @param z
	 */
	protected void openDoubleDoor(World world, int x, int y, int z)
	{
		int metadata = getFullMetadata(world, x, y, z);
		boolean opened = (metadata & flagOpened) != 0;
		int dir = metadata & 3;

		if (dir == DIR_NORTH || dir == DIR_SOUTH)
		{
			if (this.isDoubleDoor(world, x - 1, y, z, metadata))
				setDoorState(world, x - 1, y, z, opened ? stateClosing : stateOpening);
			if (this.isDoubleDoor(world, x + 1, y, z, metadata))
				setDoorState(world, x + 1, y, z, opened ? stateClosing : stateOpening);
		}
		else if (dir == DIR_EAST || dir == DIR_WEST)
		{
			if (this.isDoubleDoor(world, x, y, z - 1, metadata))
				setDoorState(world, x, y, z - 1, opened ? stateClosing : stateOpening);
			if (this.isDoubleDoor(world, x, y, z + 1, metadata))
				setDoorState(world, x, y, z + 1, opened ? stateClosing : stateOpening);
		}

	}

	// #region Events
	public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer p, int par6, float par7, float par8, float par9)
	{
		if (blockMaterial == Material.iron)
			return false;

		boolean opened = (getFullMetadata(world, x, y, z) & flagOpened) != 0;
		setDoorState(world, x, y, z, opened ? stateClosing : stateOpening);
		openDoubleDoor(world, x, y, z);

		return true;
	}

	public void onPoweredBlockChange(World world, int x, int y, int z, boolean powered)
	{
		boolean opened = (getFullMetadata(world, x, y, z) & flagOpened) != 0;
		DoorTileEntity te = (DoorTileEntity) world.getBlockTileEntity(x, y, z);
		if ((opened != powered) || (te != null && te.moving))
		{
			setDoorState(world, x, y, z, powered ? stateOpening : stateClosing);
			openDoubleDoor(world, x, y, z);
		}
	}

	public void onNeighborBlockChange(World world, int x, int y, int z, int blockID)
	{
		int metadata = world.getBlockMetadata(x, y, z);

		if ((metadata & flagTopBlock) == 0)
		{
			boolean flag = false;

			if (world.getBlockId(x, y + 1, z) != this.blockID)
			{
				world.setBlockToAir(x, y, z);
				flag = true;
			}

			if (!world.doesBlockHaveSolidTopSurface(x, y - 1, z))
			{
				world.setBlockToAir(x, y, z);
				flag = true;

				if (world.getBlockId(x, y + 1, z) == this.blockID)
					world.setBlockToAir(x, y + 1, z);
			}

			if (flag)
			{
				if (!world.isRemote)
					dropBlockAsItem(world, x, y, z, metadata, 0);
			}
			else
			{
				boolean flag1 = world.isBlockIndirectlyGettingPowered(x, y, z) || world.isBlockIndirectlyGettingPowered(x, y + 1, z);

				if ((flag1 || blockID > 0 && Block.blocksList[blockID].canProvidePower()) && blockID != this.blockID)
					onPoweredBlockChange(world, x, y, z, flag1);
			}
		}
		else
		{
			if (world.getBlockId(x, y - 1, z) != this.blockID)
				world.setBlockToAir(x, y, z);

			if (blockID > 0 && blockID != this.blockID)
				onNeighborBlockChange(world, x, y - 1, z, blockID);
		}
	}

	// #end Events

	public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z, boolean selBox)
	{
		int metadata = getFullMetadata(world, x, y, z);
		boolean topBlock = (metadata & flagTopBlock) != 0;
		DoorTileEntity te = (DoorTileEntity) world.getBlockTileEntity(x, y - (topBlock ? 1 : 0), z);
		if (te != null && te.moving)
		{
			setBlockBounds(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
			return;
		}
		float[][] bounds = calculateBlockBounds(metadata, selBox);
		setBlockBounds(bounds[0][0], bounds[0][1], bounds[0][2], bounds[1][0], bounds[1][1], bounds[1][2]);
	}

	public double[][] calculateBlockBoundsD(int metadata, boolean selBox)
	{
		float[][] bounds = calculateBlockBounds(metadata, selBox);
		return new double[][] { { bounds[0][0], bounds[0][1], bounds[0][2] }, { bounds[1][0], bounds[1][1], bounds[1][2] } };
	}

	public float[][] calculateBlockBounds(int metadata, boolean selBox)
	{
		float f = DOOR_WIDTH;
		int dir = metadata & 3;
		boolean topBlock = (metadata & flagTopBlock) != 0;
		boolean opened = (metadata & flagOpened) != 0;
		boolean reversed = (metadata & flagReversed) != 0;

		float x = 0;
		float y = 0;
		float z = 0;
		float X = 1;
		float Y = 1;
		float Z = 1;

		if (selBox)
		{
			y -= (topBlock ? 1 : 0);
			Y += (topBlock ? 0 : 1);
		}

		if ((dir == DIR_NORTH && !opened) || (dir == DIR_WEST && opened && !reversed) || (dir == DIR_EAST && opened && reversed))
			Z = f;
		else if ((dir == DIR_WEST && !opened) || (dir == DIR_SOUTH && opened && !reversed) || (dir == DIR_NORTH && opened && reversed))
			X = f;
		else if ((dir == DIR_EAST && !opened) || (dir == DIR_NORTH && opened && !reversed) || (dir == DIR_SOUTH && opened && reversed))
			x = 1 - f;
		else if ((dir == DIR_SOUTH && !opened) || (dir == DIR_EAST && opened && !reversed) || (dir == DIR_WEST && opened && reversed))
			z = 1 - f;

		return new float[][] { { x, y, z }, { X, Y, Z } };
	}

	public boolean canPlaceBlockAt(World world, int x, int y, int z)
	{
		return y >= 255 ? false : world.doesBlockHaveSolidTopSurface(x, y - 1, z) && super.canPlaceBlockAt(world, x, y, z)
				&& super.canPlaceBlockAt(world, x, y + 1, z);
	}

	public int getFullMetadata(IBlockAccess world, int x, int y, int z)
	{
		int metadata = world.getBlockMetadata(x, y, z);
		boolean blockTop = (metadata & flagTopBlock) != 0;
		int bottomMetadata;
		int topMetadata;

		if (blockTop)
		{
			bottomMetadata = world.getBlockMetadata(x, y - 1, z);
			topMetadata = metadata;
		}
		else
		{
			bottomMetadata = metadata;
			topMetadata = world.getBlockMetadata(x, y + 1, z);
		}

		boolean flag1 = (topMetadata & 1) != 0;
		return bottomMetadata & 7 | (blockTop ? flagTopBlock : 0) | (flag1 ? flagReversed : 0);
	}

	@SideOnly(Side.CLIENT)
	public int idPicked(World par1World, int par2, int par3, int par4)
	{
		return blockMaterial == Material.iron ? Item.doorIron.itemID : Item.doorWood.itemID;
	}

	public void onBlockHarvested(World world, int x, int y, int z, int metadata, EntityPlayer p)
	{
		if (p.capabilities.isCreativeMode && (metadata & flagTopBlock) != 0 && world.getBlockId(x, y - 1, z) == blockID)
		{
			world.setBlockToAir(x, y - 1, z);
		}
	}

	public int idDropped(int metadata, Random par2Random, int par3)
	{
		return (metadata & flagTopBlock) != 0 ? 0 : (blockMaterial == Material.iron ? Item.doorIron.itemID : Item.doorWood.itemID);
	}

	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z)
	{
		setBlockBoundsBasedOnState(world, x, y, z, true);
		return super.getSelectedBoundingBoxFromPool(world, x, y, z);
	}

	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z)
	{
		setBlockBoundsBasedOnState(world, x, y, z, false);
		return super.getCollisionBoundingBoxFromPool(world, x, y, z);
	}

	public MovingObjectPosition collisionRayTrace(World world, int x, int y, int z, Vec3 par5Vec3, Vec3 par6Vec3)
	{
		setBlockBoundsBasedOnState(world, x, y, z, false);
		return super.collisionRayTrace(world, x, y, z, par5Vec3, par6Vec3);
	}

	public int getMobilityFlag()
	{
		return 1;
	}

	public boolean isOpaqueCube()
	{
		return false;
	}

	public boolean getBlocksMovement(IBlockAccess world, int x, int y, int z)
	{
		return (getFullMetadata(world, x, y, z) & flagOpened) != 0;
	}

	public boolean renderAsNormalBlock()
	{
		return false;
	}

	public int getRenderType()
	{
		return DoorRenderer.renderId;
		// return 7;
	}

	@Override
	public TileEntity createNewTileEntity(World world)
	{
		return new DoorTileEntity();
	}

	@Override
	public TileEntity createTileEntity(World world, int metadata)
	{
		return new DoorTileEntity();
	}
}
