# -*- coding: utf-8 -*-
"""
Created on Sat Nov 30 19:46:51 2019

@author: Mingan Huang

In order to run this code in VM, use the command "sudo python3 distMeasurement.py"
Also make sure that the "targets.txt" is in the same directory

"""

# import libraries
import struct
import time
import socket
import select
from socket import SOCK_RAW, AF_PACKET
from random import randint

# define the payload
msg = 'measurement for class project. questions to student mxh805@case.edu or professor mxr136@case.edu'
payload = bytes(msg + 'a'*(1472 - len(msg)),'ascii')

# define the maximum number of hops
max_hops = 32

# Specially allocated port number for probing
port = 33434


"""
Create a udp socket 
input: ttl (time to live)
return: the udp socket
"""
def init_sending_socket(ttl):
    sending_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
    sending_socket.setsockopt(socket.SOL_IP, socket.IP_TTL, ttl)
    
    return sending_socket

"""
Create a ICMP socket
input: ttl (time to live)
return: the ICMP socket
"""
def init_receiving_socket(ttl):
    receving_socket = socket.socket(socket.AF_INET,socket.SOCK_RAW, socket.IPPROTO_ICMP)
    receving_socket.setsockopt(socket.IPPROTO_IP, socket.IP_HDRINCL, 1)
    
    timeout = struct.pack("ll", 5, 0)
    receving_socket.setsockopt(socket.SOL_SOCKET, socket.SO_RCVTIMEO, timeout)
    
    return receving_socket

"""
A helper method to check if x match y
"""
def match(x, y):
    if x == y:
        return True
    else:
        return False

"""
Run the traceroute 
input: destination name
return: the number of hops, rtt, and the bytes of original message
"""
def ICMP_response(dest):
    # Get the ip address of the website
    try:
        dest_ip = socket.gethostbyname(dest)
    except socket.error:
        print("Failed to resolve host " + str(dest_ip))
    
    # Record the time
    sending_time = time.time()
    
    # Initiate two sockets
    sending_socket = init_sending_socket(max_hops)
    receving_socket = init_receiving_socket(max_hops)
    receving_socket.bind(("", port))
    print("Currently sending to "+ dest + "\n")
    
    # Send the payload
    sending_socket.sendto(payload, (dest_ip, port))
    arriving_packets = select.select([receving_socket], [], [], 2)
    
    # Default value 
    failed_to_reach = True
    
    # maximum number of attempts for a single website
    max_attempts = 3
    
    # default number of techniques produced a match
    matches = 0
    
    while failed_to_reach and arriving_packets and max_attempts > 0:
        try:
            data, addr = receving_socket.recvfrom(1500)
            #print("message: " + str(data))
            response_addr = addr[0]
            #print("response: " + str(response_addr))
            ICMP_header = data[20:22]
            type, code = struct.unpack('bb', ICMP_header)
            #print("type: " + str(type))
            #print("code: " + str(code))
            ip_packed_header = data[28:48]
            ip_header = struct.unpack('!BBHHHBBH4s4s', ip_packed_header)
            ip_id = ip_header[3]
            #print("IP id: " + str(ip_id))
            ip_source_addr = socket.inet_ntoa(ip_header[8]) # we are not using this 
            #print("IP header source: " + str(ip_source_addr))
            ip_dest_addr = socket.inet_ntoa(ip_header[9])
            #print("IP header destination address: " + str(ip_dest_addr))
            ICMP_id = struct.unpack('!H', data[32:34])[0]
            #print("ICMP id: " + str(ICMP_id))
            
            # get dest port from UDP header
            # 28 B offset from IP/ICMP, 20B offset from old IP header, dest port is byte 2-3 of UDP header
            dest_port = struct.unpack('!H', data[50:52])[0]
            
            # check if the third technique produced a match
            if match(dest_port, port):
                matches += 1
            
            # check if the second technique produced a match
            if match(ip_id, ICMP_id):
                matches += 1
                
            # check if the first technique produced a match
            if match(ip_dest_addr, dest_ip):
                matches += 1
            
            # if the message succesfully connected to the website, otherwise it is a timeout
            if dest_port == port or ip_id == ICMP_id or ip_dest_addr == dest_ip:
                failed_to_reach = False
                
                # Get the remaining time to live
                ip_ttl = ip_header[5]
                
                # Calculate the number of hops
                hop_count = max_hops - ip_ttl
                
                # Calculate the RTT
                stop_time = time.time()
                rtt = stop_time - sending_time
                
                """
                parses header to get the total length of the datagram. 
				We subtract 28 bytes because 20 represent the new IP header, and 8 represent the ICMP header.
				This leaves us with the 20 byte original header and some of the original payload
				"""
                msg_len = struct.unpack("!xxH", data[0:4])[0] - 28
                
                # Close the sockets if we have what we need
                sending_socket.close()
                receving_socket.close()
                
                return (hop_count, float(rtt * 1000), matches, msg_len)
        
        # if the socket timesout (avoid infinity loop)        
        except socket.error:
            max_attempts -= 1
            print("Failed to receive response, " + str(max_attempts) + " more tries remaining")
            sending_time = time.time()
            sending_socket.sendto(payload, (dest_ip, port))
        
    print("Failed to connect, we are moving on next website \n")
    sending_socket.close()
    receving_socket.close()
    return (-1, -1, -1)

"""
main method
Open the targets.txt for a list of websites
Write results into results.csv
"""
def main():
    dest_list = open("targets.txt").read().splitlines()
    output = open("results.csv",'w')
    # write the title 
    output.write('%s, %s, %s, %s, %s\n' % ("Site", "Number of Hops", "RTT", "number of techniques produced match", "Bytes of Original Message"))
    
    for dest in dest_list:
        hop_count, rtt, matches, msg_len = ICMP_response(dest)
        output.write('%s, %d, %f, %d, %d\n' % (dest, hop_count, rtt, matches, msg_len))
        print("Number of hops = " + str(hop_count) + "(-1 if failed to connect)")
        print("RTT = " + str(rtt) + "(-1 if failed to connect)")
        print(("Bytes of original message = " + str(msg_len) + "(-1 if failed to connect)\n"))


if __name__ == "__main__":
    main()