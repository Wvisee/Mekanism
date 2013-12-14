package mekanism.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import mekanism.api.transmitters.DynamicNetwork;
import mekanism.api.transmitters.ITransmitter;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.tileentity.TileEntityMechanicalPipe;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.Event;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;
import cpw.mods.fml.common.FMLCommonHandler;

public class FluidNetwork extends DynamicNetwork<IFluidHandler, FluidNetwork>
{
	public static final int PIPE_FLUID = 1000;
	
	public int transferDelay = 0;
	
	public boolean didTransfer;
	public boolean prevTransfer;
	
	public float fluidScale;
	public Fluid refFluid = null;
	
	public FluidStack fluidStored;
	
	public FluidNetwork(ITransmitter<FluidNetwork>... varPipes)
	{
		transmitters.addAll(Arrays.asList(varPipes));
		register();
	}
	
	public FluidNetwork(Collection<ITransmitter<FluidNetwork>> collection)
	{
		transmitters.addAll(collection);
		register();
	}
	
	public FluidNetwork(Set<FluidNetwork> networks)
	{
		for(FluidNetwork net : networks)
		{
			if(net != null)
			{
				if(net.refFluid != null && net.fluidScale > fluidScale)
				{
					refFluid = net.refFluid;
					fluidScale = net.fluidScale;
				}
				
				addAllTransmitters(net.transmitters);
				net.deregister();
			}
		}
		
		refresh();
		register();
	}
	
	public int getCapacity()
	{
		return PIPE_FLUID*transmitters.size();
	}
	
	public synchronized int getFluidNeeded()
	{
		return getCapacity()-(fluidStored != null ? fluidStored.amount : 0);
	}
	
	public synchronized int tickEmit(FluidStack fluidToSend, boolean doTransfer)
	{
		List availableAcceptors = Arrays.asList(getAcceptors(fluidToSend).toArray());
		
		Collections.shuffle(availableAcceptors);
		
		int fluidSent = 0;
		
		if(!availableAcceptors.isEmpty())
		{
			int divider = availableAcceptors.size();
			int remaining = fluidToSend.amount % divider;
			int sending = (fluidToSend.amount-remaining)/divider;
			
			for(Object obj : availableAcceptors)
			{
				if(obj instanceof IFluidHandler)
				{
					IFluidHandler acceptor = (IFluidHandler)obj;
					int currentSending = sending;
					
					if(remaining > 0)
					{
						currentSending++;
						remaining--;
					}
					
					fluidSent += acceptor.fill(acceptorDirections.get(acceptor).getOpposite(), new FluidStack(fluidToSend.fluidID, currentSending), doTransfer);
				}
			}
		}
		
		if(doTransfer && fluidSent > 0 && FMLCommonHandler.instance().getEffectiveSide().isServer())
		{
			refFluid = fluidToSend.getFluid();
			didTransfer = true;
			transferDelay = 2;
		}
		
		return fluidSent;
	}
	
	public synchronized int emit(FluidStack fluidToSend, boolean doTransfer)
	{
		if(fluidToSend == null || (fluidStored != null && fluidStored.getFluid() != fluidToSend.getFluid()))
		{
			return 0;
		}
		
		int toUse = Math.min(getFluidNeeded(), fluidToSend.amount);
		
		if(doTransfer)
		{
			if(fluidStored == null)
			{
				fluidStored = fluidToSend.copy();
				fluidStored.amount = toUse;
			}
			else {
				fluidStored.amount += toUse;
			}
		}
		
		return toUse;
	}
	
	@Override
	public void tick()
	{
		super.tick();
		
		if(FMLCommonHandler.instance().getEffectiveSide().isServer())
		{
			if(transferDelay == 0)
			{
				didTransfer = false;
			}
			else {
				transferDelay--;
			}
			
			if(didTransfer != prevTransfer || needsUpdate)
			{
				MinecraftForge.EVENT_BUS.post(new FluidTransferEvent(this, refFluid != null ? refFluid.getID() : -1, didTransfer));
				needsUpdate = false;
			}
			
			prevTransfer = didTransfer;
			
			if(fluidStored != null)
			{
				fluidStored.amount -= (fluidStored.amount - tickEmit(fluidStored, true));
				
				if(fluidStored.amount <= 0)
				{
					fluidStored = null;
				}
			}
		}
	}
	
	@Override
	public void clientTick()
	{
		super.clientTick();
		
		if(didTransfer && fluidScale < 1)
		{
			fluidScale = Math.min(1, fluidScale+0.02F);
		}
		else if(!didTransfer && fluidScale > 0)
		{
			fluidScale = Math.max(0, fluidScale-0.02F);
			
			if(fluidScale == 0)
			{
				refFluid = null;
			}
		}
	}
	
	@Override
	public synchronized Set<IFluidHandler> getAcceptors(Object... data)
	{
		FluidStack fluidToSend = (FluidStack)data[0];
		Set<IFluidHandler> toReturn = new HashSet<IFluidHandler>();
		
		for(IFluidHandler acceptor : possibleAcceptors)
		{
			if(acceptorDirections.get(acceptor) == null)
			{
				continue;
			}
			
			if(acceptor.canFill(acceptorDirections.get(acceptor).getOpposite(), fluidToSend.getFluid()))
			{
				toReturn.add(acceptor);
			}
		}
		
		return toReturn;
	}
 
	@Override
	public synchronized void refresh()
	{
		Set<ITransmitter<FluidNetwork>> iterPipes = (Set<ITransmitter<FluidNetwork>>)transmitters.clone();
		Iterator it = iterPipes.iterator();
		
		possibleAcceptors.clear();
		acceptorDirections.clear();

		while(it.hasNext())
		{
			ITransmitter<FluidNetwork> conductor = (ITransmitter<FluidNetwork>)it.next();

			if(conductor == null || ((TileEntity)conductor).isInvalid())
			{
				it.remove();
				transmitters.remove(conductor);
			}
			else {
				conductor.setTransmitterNetwork(this);
			}
		}
		
		for(ITransmitter<FluidNetwork> pipe : iterPipes)
		{
			if(pipe instanceof TileEntityMechanicalPipe && ((TileEntityMechanicalPipe)pipe).isActive) continue;
			
			IFluidHandler[] acceptors = PipeUtils.getConnectedAcceptors((TileEntity)pipe);
		
			for(IFluidHandler acceptor : acceptors)
			{
				if(acceptor != null && !(acceptor instanceof ITransmitter))
				{
					possibleAcceptors.add(acceptor);
					acceptorDirections.put(acceptor, ForgeDirection.getOrientation(Arrays.asList(acceptors).indexOf(acceptor)));
				}
			}
		}
	}

	@Override
	public synchronized void merge(FluidNetwork network)
	{
		if(network != null && network != this)
		{
			Set<FluidNetwork> networks = new HashSet<FluidNetwork>();
			networks.add(this);
			networks.add(network);
			FluidNetwork newNetwork = create(networks);
			newNetwork.refresh();
		}
	}
	
	public static class FluidTransferEvent extends Event
	{
		public final FluidNetwork fluidNetwork;
		
		public final int fluidType;
		public final boolean didTransfer;
		
		public FluidTransferEvent(FluidNetwork network, int type, boolean did)
		{
			fluidNetwork = network;
			fluidType = type;
			didTransfer = did;
		}
	}
		
	@Override
	public String toString()
	{
		return "[FluidNetwork] " + transmitters.size() + " transmitters, " + possibleAcceptors.size() + " acceptors.";
	}
	
	@Override
	protected FluidNetwork create(ITransmitter<FluidNetwork>... varTransmitters) 
	{
		FluidNetwork network = new FluidNetwork(varTransmitters);
		network.refFluid = refFluid;
		network.fluidScale = fluidScale;
		return network;
	}

	@Override
	protected FluidNetwork create(Collection<ITransmitter<FluidNetwork>> collection) 
	{
		FluidNetwork network = new FluidNetwork(collection);
		network.refFluid = refFluid;
		network.fluidScale = fluidScale;
		return network;
	}

	@Override
	protected FluidNetwork create(Set<FluidNetwork> networks) 
	{
		FluidNetwork network = new FluidNetwork(networks);
		
		if(refFluid != null && fluidScale > network.fluidScale)
		{
			network.refFluid = refFluid;
			network.fluidScale = fluidScale;
		}
		
		return network;
	}
	
	@Override
	public TransmissionType getTransmissionType()
	{
		return TransmissionType.FLUID;
	}

	@Override
	public String getNeeded()
	{
		return "Fluid needed (any type): " + (float)getFluidNeeded()/1000F + " buckets";
	}
	
	@Override
	public String getFlow()
	{
		return fluidStored + " mB";
	}
}
