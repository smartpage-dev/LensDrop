import sys,sqlite3,base64
con=sqlite3.connect(sys.argv[1],isolation_level=None)
def dec(s):
 if s=='N':return None
 if s.startswith('I'):return int(s[1:])
 return base64.b64decode(s[1:]).decode()
def enc(x):
 if x is None:return 'N'
 if isinstance(x,int):return 'I'+str(x)
 return 'S'+base64.b64encode(str(x).encode()).decode()
for line in sys.stdin:
 try:
  p=line.rstrip('\n').split('\t')
  if p[0]=='QUIT':break
  sql=base64.b64decode(p[1]).decode();args=[dec(x) for x in p[2:]]
  cur=con.execute(sql,args)
  if cur.description:
   cols=[x[0] for x in cur.description];rows=cur.fetchall()
   print('DATA\t'+str(len(cols))+'\t'+str(len(rows)))
   print('\t'.join(enc(x) for x in cols))
   for row in rows:print('\t'.join(enc(x) for x in row))
  else:print('OK')
 except Exception as e:print('ERR\t'+base64.b64encode(str(e).encode()).decode())
 sys.stdout.flush()
con.close()

